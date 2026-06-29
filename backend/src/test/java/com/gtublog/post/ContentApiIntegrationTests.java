package com.gtublog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@Tag("docker")
@SpringBootTest
class ContentApiIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";

    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_content")
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
    private com.gtublog.auth.AuthProperties authProperties;

    @Autowired
    void configureMockMvc(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM post_tag");
        jdbcTemplate.update("DELETE FROM post_category");
        jdbcTemplate.update("DELETE FROM post_revision");
        jdbcTemplate.update("DELETE FROM post_view_counter");
        jdbcTemplate.update("DELETE FROM post");
        jdbcTemplate.update("DELETE FROM tag");
        jdbcTemplate.update("DELETE FROM category");
        jdbcTemplate.update("DELETE FROM audit_entry");
    }

    @Test
    void adminEndpointsRequireAdminJwt() throws Exception {
        mockMvc.perform(get("/api/v1/admin/posts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void supportsTaxonomyAndPostLifecycleIncludingPublicDiscoveryAndRevisionRestore() throws Exception {
        var bearerToken = bearerToken();

        var categoryId = taxonomyId(mockMvc.perform(post("/api/v1/admin/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"개발","description":"개발 카테고리"}
                                """))
                .andExpect(status().isCreated())
                .andReturn());

        var tagId = taxonomyId(mockMvc.perform(post("/api/v1/admin/taxonomy/tags")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"스프링","description":"스프링 태그"}
                                """))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(get("/api/v1/admin/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/taxonomy/tags/{id}", tagId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"spring-boot","name":"스프링 부트","description":"업데이트된 태그"}
                                """))
                .andExpect(status().isOk());

        var createResult = mockMvc.perform(post("/api/v1/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug":"spring-boot-post",
                                  "title":"Spring Boot 자동화 블로그 초안",
                                  "excerpt":"초안 요약",
                                  "contentMarkdown":"초안 본문",
                                  "contentHtml":"<p>초안 본문</p>",
                                  "sourceFingerprint":"story-4-draft",
                                  "categoryIds":[%d],
                                  "tagIds":[%d],
                                  "revisionNote":"초안 작성"
                                }
                                """.formatted(categoryId, tagId)))
                .andExpect(status().isCreated())
                .andReturn();

        var createdPost = jsonBody(createResult);
        var postId = createdPost.get("id").asLong();
        var slug = createdPost.get("slug").asText();

        assertThat(createdPost.get("status").asText()).isEqualTo("DRAFT");
        assertThat(slug).isEqualTo("spring-boot-post");

        mockMvc.perform(put("/api/v1/admin/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug":"spring-boot-post",
                                  "title":"Spring Boot 자동화 블로그 공개본",
                                  "excerpt":"공개 요약",
                                  "contentMarkdown":"공개 본문과 keyword",
                                  "contentHtml":"<p>공개 본문과 keyword</p>",
                                  "sourceFingerprint":"story-4-draft",
                                  "categoryIds":[%d],
                                  "tagIds":[%d],
                                  "revisionNote":"공개 전 수정"
                                }
                                """.formatted(categoryId, tagId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/posts/{id}/publish", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/posts"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var json = jsonBody(result);
                    assertThat(json.get("items")).hasSize(1);
                    assertThat(json.at("/items/0/title").asText()).isEqualTo("Spring Boot 자동화 블로그 공개본");
                    assertThat(json.at("/items/0/categories/0").asText()).isEqualTo("개발");
                    assertThat(json.at("/items/0/tags/0").asText()).isEqualTo("스프링 부트");
                });

        mockMvc.perform(get("/api/v1/public/search").queryParam("q", "keyword"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(jsonBody(result).at("/items/0/slug").asText()).isEqualTo(slug));

        mockMvc.perform(get("/api/v1/public/categories/{slug}", "개발"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(jsonBody(result).at("/items/0/slug").asText()).isEqualTo(slug));

        mockMvc.perform(get("/api/v1/public/tags/{slug}", "spring-boot"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(jsonBody(result).at("/items/0/slug").asText()).isEqualTo(slug));

        mockMvc.perform(get("/api/v1/public/archive"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(jsonBody(result).get(0).get("count").asLong()).isEqualTo(1L));

        mockMvc.perform(get("/api/v1/public/posts/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(jsonBody(result).get("viewCount").asLong()).isEqualTo(1L));

        mockMvc.perform(get("/api/v1/public/posts/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(jsonBody(result).get("viewCount").asLong()).isEqualTo(2L));

        mockMvc.perform(get("/api/v1/admin/posts/{id}/revisions", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var json = jsonBody(result);
                    assertThat(json).hasSize(2);
                    assertThat(json.get(0).get("revisionNumber").asInt()).isEqualTo(2);
                    assertThat(json.get(1).get("revisionNumber").asInt()).isEqualTo(1);
                });

        mockMvc.perform(post("/api/v1/admin/posts/{id}/revisions/{revisionNumber}/restore", postId, 1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var json = jsonBody(result);
                    assertThat(json.get("title").asText()).isEqualTo("Spring Boot 자동화 블로그 초안");
                    assertThat(json.get("status").asText()).isEqualTo("PUBLISHED");
                });

        mockMvc.perform(post("/api/v1/admin/posts/{id}/archive", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(jsonBody(result).get("status").asText()).isEqualTo("ARCHIVED"));

        mockMvc.perform(get("/api/v1/public/posts/{slug}", slug))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/admin/posts/{id}/delete", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(jsonBody(result).get("status").asText()).isEqualTo("DELETED"));

        mockMvc.perform(post("/api/v1/admin/posts/{id}/restore", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(jsonBody(result).get("status").asText()).isEqualTo("DRAFT"));

        mockMvc.perform(get("/api/v1/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(jsonBody(result).at("/items/0/status").asText()).isEqualTo("DRAFT"));

        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var json = jsonBody(result);
                    assertThat(json.at("/items/0/actionType").asText()).isEqualTo("POST_RESTORED");
                    assertThat(json.at("/totalElements").asInt()).isGreaterThanOrEqualTo(1);
                });
    }

    private long taxonomyId(MvcResult result) throws Exception {
        return jsonBody(result).get("id").asLong();
    }

    private JsonNode jsonBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
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
        var token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(RS256).build(), claims)).getTokenValue();
        return "Bearer " + token;
    }
}
