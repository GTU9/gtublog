package com.gtublog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gtublog.audit.AuditEntryRepository;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@Tag("docker")
@SpringBootTest
@Import(AuthIntegrationTests.TestAdminProbeController.class)
class AuthIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";

    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_auth")
            .withUsername("gtublog")
            .withPassword("gtublog-test-password");

    static {
        MYSQL.start();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/mysql")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthProperties authProperties;

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    @Autowired
    void configureMockMvc(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @BeforeEach
    void resetAuthState() {
        jdbcTemplate.update("DELETE FROM refresh_token WHERE predecessor_id IS NOT NULL");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM refresh_token_family");
        jdbcTemplate.update("DELETE FROM audit_entry");
        jdbcTemplate.update("UPDATE admin_user SET last_login_at = NULL");
    }

    @Test
    void loginIssuesAccessTokenAndCookies() throws Exception {
        var result = login();
        var response = result.getResponse();
        var json = jsonBody(result);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(json.get("accessToken").asText()).isNotBlank();
        assertThat(json.get("csrfToken").asText()).isNotBlank();
        assertThat(json.get("admin").get("username").asText()).isEqualTo("admin");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(value -> value.startsWith("gtublog_refresh="))
                .anyMatch(value -> value.startsWith("gtublog_csrf="));
        assertThat(auditEntryRepository.findAll())
                .extracting("actionType")
                .contains("AUTH_LOGIN_SUCCESS", "AUTH_REFRESH_ISSUED");
    }

    @Test
    void sessionAndAdminEndpointEnforceJwtValidationRules() throws Exception {
        var validToken = accessToken(List.of("ROLE_ADMIN"), authProperties.audience(), Instant.now().plusSeconds(300));

        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isOk());

        var wrongAudience = accessToken(List.of("ROLE_ADMIN"), "wrong-audience", Instant.now().plusSeconds(300));
        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongAudience))
                .andExpect(status().isUnauthorized());

        var malformed = "not-a-jwt";
        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + malformed))
                .andExpect(status().isUnauthorized());

        var missingRole = accessToken(List.of(), authProperties.audience(), Instant.now().plusSeconds(300));
        mockMvc.perform(get("/api/v1/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + missingRole))
                .andExpect(status().isForbidden());

        var wrongSignature = alternateSignatureToken();
        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongSignature))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRequiresExactOriginAndCsrfBinding() throws Exception {
        var loginResult = login();
        var refreshCookie = cookieValue(loginResult, "gtublog_refresh");
        var csrfToken = jsonBody(loginResult).get("csrfToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://malicious.example")
                        .header("X-CSRF-Token", csrfToken)
                        .cookie(cookie("gtublog_refresh", refreshCookie)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .header("X-CSRF-Token", "wrong-token")
                        .cookie(cookie("gtublog_refresh", refreshCookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshReplayRevokesFamilyAndInvalidatesNewestToken() throws Exception {
        var loginResult = login();
        var originalRefreshCookie = cookieValue(loginResult, "gtublog_refresh");
        var originalCsrf = jsonBody(loginResult).get("csrfToken").asText();

        var refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .header("X-CSRF-Token", originalCsrf)
                        .cookie(cookie("gtublog_refresh", originalRefreshCookie)))
                .andExpect(status().isOk())
                .andReturn();

        var rotatedRefreshCookie = cookieValue(refreshResult, "gtublog_refresh");
        var rotatedCsrf = jsonBody(refreshResult).get("csrfToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .header("X-CSRF-Token", originalCsrf)
                        .cookie(cookie("gtublog_refresh", originalRefreshCookie)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .header("X-CSRF-Token", rotatedCsrf)
                        .cookie(cookie("gtublog_refresh", rotatedRefreshCookie)))
                .andExpect(status().isUnauthorized());

        assertThat(auditEntryRepository.findAll())
                .extracting("actionType")
                .contains("AUTH_REFRESH_ROTATED", "AUTH_REFRESH_REPLAY_DETECTED");
    }

    @Test
    void allowedCorsPreflightSucceedsAndRejectedOriginFails() throws Exception {
        mockMvc.perform(options("/api/v1/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-CSRF-Token"))
                .andExpect(status().isOk());

        mockMvc.perform(options("/api/v1/auth/refresh")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-CSRF-Token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentRefreshAllowsOneSuccessAndRevokesFamilyOnReuse() throws Exception {
        var loginResult = login();
        var refreshCookie = cookieValue(loginResult, "gtublog_refresh");
        var csrfToken = jsonBody(loginResult).get("csrfToken").asText();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("Origin", "http://localhost:3000")
                            .header("X-CSRF-Token", csrfToken)
                            .cookie(cookie("gtublog_refresh", refreshCookie)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            var futures = executor.invokeAll(List.of(call, call));
            var statuses = futures.stream().map(future -> {
                try {
                    return future.get();
                }
                catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).toList();

            assertThat(statuses).contains(200);
            assertThat(statuses).anyMatch(status -> status == 401 || status == 403);
        }
    }

    private MvcResult login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin-test-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String accessToken(List<String> roles, String audience, Instant expiresAt) {
        var claims = JwtClaimsSet.builder()
                .issuer(authProperties.issuer())
                .audience(List.of(audience))
                .subject("1")
                .issuedAt(Instant.now())
                .notBefore(Instant.now())
                .expiresAt(expiresAt)
                .claim("username", "admin")
                .claim("displayName", "Test Administrator")
                .claim("roles", roles)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(RS256).build(), claims)).getTokenValue();
    }

    private String alternateSignatureToken() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var rsaKey = new com.nimbusds.jose.jwk.RSAKey.Builder((java.security.interfaces.RSAPublicKey) pair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) pair.getPrivate())
                .keyID("alternate")
                .build();
        var encoder = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
                new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(rsaKey)));
        var claims = JwtClaimsSet.builder()
                .issuer(authProperties.issuer())
                .audience(List.of(authProperties.audience()))
                .subject("1")
                .issuedAt(Instant.now())
                .notBefore(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("username", "admin")
                .claim("displayName", "Test Administrator")
                .claim("roles", List.of("ROLE_ADMIN"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(RS256).build(), claims)).getTokenValue();
    }

    private JsonNode jsonBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private jakarta.servlet.http.Cookie cookie(String name, String value) {
        return new jakarta.servlet.http.Cookie(name, value);
    }

    private String cookieValue(MvcResult result, String name) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .map(header -> header.substring((name + "=").length(), header.indexOf(';')))
                .orElseThrow();
    }

    @RestController
    static class TestAdminProbeController {

        @GetMapping("/api/v1/admin/probe")
        Map<String, String> probe() {
            return Map.of("status", "ok");
        }
    }
}
