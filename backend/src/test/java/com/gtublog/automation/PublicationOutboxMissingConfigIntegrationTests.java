package com.gtublog.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("docker")
@SpringBootTest(properties = {
        "spring.quartz.auto-startup=false",
        "app.automation.revalidation.base-url=",
        "app.automation.revalidation.shared-secret=story-24-revalidation-secret-32-bytes-minimum"
})
class PublicationOutboxMissingConfigIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";

    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_publication_outbox_missing_config")
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
        registry.add("app.automation.revalidation.retry-initial-delay", () -> "PT30S");
        registry.add("app.automation.revalidation.retry-max-delay", () -> "PT15M");
        registry.add("app.automation.revalidation.request-timeout", () -> "PT5S");
        registry.add("app.automation.revalidation.lease-duration", () -> "PT30S");
        registry.add("app.automation.revalidation.poll-interval", () -> "PT30S");
        registry.add("app.automation.revalidation.max-attempts", () -> "5");
        registry.add("app.automation.revalidation.batch-size", () -> "20");
    }

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private com.gtublog.auth.AuthProperties authProperties;

    @Autowired
    void configureMockMvc(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM publication_outbox_event");
        jdbcTemplate.update("DELETE FROM audit_entry");
    }

    @Test
    void missingRevalidationEndpointConfigurationDoesNotMarkPendingEventsDelivered() throws Exception {
        var eventKey = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO publication_outbox_event
                    (event_key, aggregate_type, aggregate_id, event_type, delivery_status, payload_json, available_at)
                VALUES
                    (?, 'POST', 901, 'POST_PUBLISHED', 'PENDING', ?, UTC_TIMESTAMP(6))
                """,
                eventKey,
                "{\"postId\":901,\"slug\":\"missing-config\",\"paths\":[\"/posts/missing-config\"]}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/automation/outbox/process")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk());

        var row = jdbcTemplate.queryForMap("""
                SELECT delivery_status, attempt_count, failure_reason
                  FROM publication_outbox_event
                 WHERE event_key = ?
                """, eventKey);
        assertThat(row.get("delivery_status")).isNotEqualTo("DELIVERED");
        assertThat(((Number) row.get("attempt_count")).intValue()).isEqualTo(1);
        assertThat(row.get("failure_reason")).asString().containsIgnoringCase("revalidation");
    }

    private String bearerToken() {
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
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(RS256).build(), claims)).getTokenValue();
    }
}
