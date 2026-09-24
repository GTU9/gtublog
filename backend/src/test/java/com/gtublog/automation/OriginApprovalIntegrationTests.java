package com.gtublog.automation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.gtublog.auth.AuthProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

@Tag("docker")
@SpringBootTest(properties = "spring.quartz.auto-startup=false")
class OriginApprovalIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";
    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_origin_approval")
            .withUsername("gtublog")
            .withPassword("gtublog-test-password");
    private static final WireMockServer WIREMOCK = new WireMockServer(
            WireMockConfiguration.wireMockConfig().bindAddress("0.0.0.0").dynamicPort()
                    .useChunkedTransferEncoding(Options.ChunkedEncodingPolicy.BODY_FILE));

    static {
        MYSQL.start();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/mysql")
                .load()
                .migrate();
    }

    @BeforeAll
    static void startWireMock() {
        WIREMOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AutomationAdminService automationAdminService;
    @Autowired private AutomationScheduleSynchronizer scheduleSynchronizer;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private AuthProperties authProperties;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SourceUrlPolicy sourceUrlPolicy;
    @Autowired private PinnedSourceHttpClient pinnedSourceHttpClient;
    private MockMvc mockMvc;

    @Autowired
    void configureMockMvc(WebApplicationContext context) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @BeforeEach
    void resetState() {
        scheduleSynchronizer.clearAutomationSchedules();
        WIREMOCK.resetAll();
        jdbcTemplate.update("DELETE FROM post_revision_source_snapshot");
        jdbcTemplate.update("DELETE FROM publication_outbox_event");
        jdbcTemplate.update("DELETE FROM post_revision");
        jdbcTemplate.update("DELETE FROM post");
        jdbcTemplate.update("DELETE FROM source_snapshot");
        jdbcTemplate.update("DELETE FROM generation_job");
        jdbcTemplate.update("DELETE FROM automation_run");
        jdbcTemplate.update("DELETE FROM automation_schedule");
        jdbcTemplate.update("DELETE FROM automation_origin_approval");
        jdbcTemplate.update("DELETE FROM automation_origin_group");
        jdbcTemplate.update("DELETE FROM automation_source");
        jdbcTemplate.update("DELETE FROM automation_topic");
        jdbcTemplate.update("DELETE FROM audit_entry");
    }

    @Test
    void cleanMigrationCreatesApprovalTablesAndNullableSnapshotProvenance() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '15' AND success = 1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'source_snapshot'
                AND column_name IN ('origin_approval_id', 'origin_approval_revision', 'origin_group_id')
                """, String.class))
                .containsExactlyInAnyOrder("origin_approval_id", "origin_approval_revision", "origin_group_id");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'source_snapshot'
                AND column_name IN ('origin_approval_id', 'origin_approval_revision', 'origin_group_id')
                AND is_nullable = 'YES'
                """, Integer.class)).isEqualTo(3);
    }

    @Test
    void onlyAdminCanCreateGroupsAndApprovalsAndActionsAreAudited() throws Exception {
        var source = createSource("/auth-feed.xml", AutomationSourceType.RSS);
        var groupPath = "/api/v1/admin/automation/topics/" + source.topicId() + "/origin-groups";
        mockMvc.perform(post(groupPath).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Primary reporting\",\"rationale\":\"Editorial review\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(groupPath).header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_READER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Primary reporting\",\"rationale\":\"Editorial review\"}"))
                .andExpect(status().isForbidden());

        var group = createGroup(source.topicId());
        assertThat(group.get("rationale").asText()).isEqualTo("Editorial review");
        var approval = approve(source.id(), group.get("id").asLong(), "127.0.0.1");
        assertThat(approval.get("active").asBoolean()).isTrue();
        assertThat(approval.get("revision").asLong()).isEqualTo(1);
        assertThat(approval.get("rationale").asText()).isEqualTo("Checked article ownership");
        assertThat(approval.get("approvedAt").asText()).isNotBlank();

        var listed = body(mockMvc.perform(get("/api/v1/admin/automation/sources/{sourceId}/origin-approvals", source.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN")))
                .andExpect(status().isOk()).andReturn());
        assertThat(listed.size()).isEqualTo(1);
        assertThat(listed.get(0).get("id").asLong()).isEqualTo(approval.get("id").asLong());
        assertThat(jdbcTemplate.queryForList("""
                SELECT action_type FROM audit_entry
                WHERE action_type IN ('ORIGIN_GROUP_CREATED', 'ORIGIN_APPROVAL_GRANTED')
                """, String.class)).containsExactlyInAnyOrder("ORIGIN_GROUP_CREATED", "ORIGIN_APPROVAL_GRANTED");
    }

    @Test
    void duplicateApprovalAndStaleRevokeConflictButCurrentRevisionCanRevoke() throws Exception {
        var source = createSource("/conflict-feed.xml", AutomationSourceType.RSS);
        var groupId = createGroup(source.topicId()).get("id").asLong();
        var approval = approve(source.id(), groupId, "127.0.0.1");
        mockMvc.perform(post("/api/v1/admin/automation/sources/{sourceId}/origin-approvals", source.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originHost\":\"127.0.0.1\",\"groupId\":%d,\"rationale\":\"Duplicate\"}".formatted(groupId)))
                .andExpect(status().isConflict());

        var revokePath = "/api/v1/admin/automation/origin-approvals/" + approval.get("id").asLong() + "/revoke";
        mockMvc.perform(post(revokePath).header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"rationale\":\"Stale edit\"}"))
                .andExpect(status().isConflict());
        var revoked = body(mockMvc.perform(post(revokePath)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1,\"rationale\":\"Ownership changed\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(revoked.get("active").asBoolean()).isFalse();
        assertThat(revoked.get("revision").asLong()).isEqualTo(2);
        assertThat(revoked.get("rationale").asText()).isEqualTo("Checked article ownership");
        assertThat(revoked.get("revocationRationale").asText()).isEqualTo("Ownership changed");
        assertThat(revoked.get("revokedAt").asText()).isNotBlank();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_entry
                WHERE action_type = 'ORIGIN_APPROVAL_REVOKED' AND target_id = ?
                """, Integer.class, Long.toString(approval.get("id").asLong()))).isEqualTo(1);
        mockMvc.perform(post(revokePath).header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1,\"rationale\":\"Again\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentApprovalOfTheSameSourceAndHostHasOneWinner() throws Exception {
        var source = createSource("/parallel-feed.xml", AutomationSourceType.RSS);
        var groupId = createGroup(source.topicId()).get("id").asLong();
        var authorization = bearerToken("ROLE_ADMIN");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var request = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent request did not start.");
                return mockMvc.perform(post("/api/v1/admin/automation/sources/{sourceId}/origin-approvals", source.id())
                                .header(HttpHeaders.AUTHORIZATION, authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"originHost\":\"127.0.0.1\",\"groupId\":%d,\"rationale\":\"Concurrent review\"}"
                                        .formatted(groupId)))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM automation_origin_approval
                WHERE source_id = ? AND origin_host = '127.0.0.1' AND active = TRUE
                """, Integer.class, source.id())).isEqualTo(1);
    }

    @Test
    void rejectsNonHostApprovalInputAndGroupsOwnedByAnotherTopic() throws Exception {
        var source = createSource("/host-validation-feed.xml", AutomationSourceType.RSS);
        var groupId = createGroup(source.topicId()).get("id").asLong();
        var authorization = bearerToken("ROLE_ADMIN");
        for (var invalidHost : List.of(
                "https://news.example.com", "news.example.com:443", "news.example.com/article", "*.example.com")) {
            mockMvc.perform(post("/api/v1/admin/automation/sources/{sourceId}/origin-approvals", source.id())
                            .header(HttpHeaders.AUTHORIZATION, authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"originHost\":\"%s\",\"groupId\":%d,\"rationale\":\"Invalid host\"}"
                                    .formatted(invalidHost, groupId)))
                    .andExpect(status().isBadRequest());
        }

        var otherSource = createSource("/other-topic-feed.xml", AutomationSourceType.RSS);
        var otherGroupId = createGroup(otherSource.topicId()).get("id").asLong();
        mockMvc.perform(post("/api/v1/admin/automation/sources/{sourceId}/origin-approvals", source.id())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originHost\":\"127.0.0.1\",\"groupId\":%d,\"rationale\":\"Wrong topic\"}"
                                .formatted(otherGroupId)))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM automation_origin_approval WHERE source_id = ?", Integer.class, source.id()))
                .isZero();
    }

    @Test
    void rssCapturesOnlyExactObservedArticleHostAndNeverPromotesHistoricalNull() throws Exception {
        var source = createSource("/provenance-feed.xml", AutomationSourceType.RSS);
        stubFeed("/provenance-feed.xml", """
                <rss version="2.0"><channel>
                  <item><title>Local article</title><link>%s/approved-article</link></item>
                  <item><title>Other article</title><link>%s/other-article</link></item>
                </channel></rss>
                """.formatted(WIREMOCK.baseUrl(), WIREMOCK.baseUrl().replace("localhost", "127.0.0.1")));
        stubArticle("/approved-article");
        stubArticle("/other-article");
        assertArticleReachable(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1") + "/other-article");

        var initialRun = automationAdminService.triggerManualRun(source.topicId(), "origin-before-approval");
        assertThat(snapshotProvenance(initialRun.id())).allSatisfy(row -> {
            assertThat(row.get("origin_approval_id")).isNull();
            assertThat(row.get("origin_group_id")).isNull();
        });
        cancelIfRunning(initialRun);

        var groupId = createGroup(source.topicId()).get("id").asLong();
        var approval = approve(source.id(), groupId, "127.0.0.1");
        var approvedRun = automationAdminService.triggerManualRun(source.topicId(), "origin-after-approval");
        assertThat(snapshotProvenance(approvedRun.id())).hasSize(2);
        assertThat(snapshotProvenance(approvedRun.id()).stream()
                .filter(row -> "127.0.0.1".equals(row.get("origin_host"))).toList())
                .singleElement().satisfies(row -> {
                    assertThat(((Number) row.get("origin_approval_id")).longValue()).isEqualTo(approval.get("id").asLong());
                    assertThat(((Number) row.get("origin_approval_revision")).longValue()).isEqualTo(1);
                    assertThat(((Number) row.get("origin_group_id")).longValue()).isEqualTo(groupId);
                });
        assertThat(snapshotProvenance(approvedRun.id()).stream()
                .filter(row -> "localhost".equals(row.get("origin_host"))).toList())
                .singleElement().satisfies(row -> assertThat(row.get("origin_approval_id")).isNull());
        assertThat(snapshotProvenance(initialRun.id())).allSatisfy(row ->
                assertThat(row.get("origin_approval_id")).isNull());

        cancelIfRunning(approvedRun);
        mockMvc.perform(post("/api/v1/admin/automation/origin-approvals/{approvalId}/revoke", approval.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1,\"rationale\":\"Source review expired\"}"))
                .andExpect(status().isOk());
        var revokedRun = automationAdminService.triggerManualRun(source.topicId(), "origin-after-revoke");
        assertThat(snapshotProvenance(revokedRun.id())).allSatisfy(row ->
                assertThat(row.get("origin_approval_id")).isNull());
        assertThat(snapshotProvenance(approvedRun.id()).stream()
                .filter(row -> "127.0.0.1".equals(row.get("origin_host"))).toList())
                .singleElement().satisfies(row ->
                        assertThat(((Number) row.get("origin_approval_id")).longValue())
                                .isEqualTo(approval.get("id").asLong()));
    }

    @Test
    void sourceUrlEditInvalidatesApprovalForNewCollectionsWithoutErasingOldEvidence() throws Exception {
        var source = createSource("/original-feed.xml", AutomationSourceType.RSS);
        stubFeed("/original-feed.xml", """
                <rss version="2.0"><channel><item><title>A</title><link>%s/article</link></item></channel></rss>
                """.formatted(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1")));
        stubFeed("/changed-feed.xml", """
                <rss version="2.0"><channel><item><title>A</title><link>%s/article</link></item></channel></rss>
                """.formatted(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1")));
        stubArticle("/article");
        assertArticleReachable(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1") + "/article");
        var groupId = createGroup(source.topicId()).get("id").asLong();
        var approval = approve(source.id(), groupId, "127.0.0.1");
        var before = automationAdminService.triggerManualRun(source.topicId(), "origin-before-source-edit");
        var runDetail = body(mockMvc.perform(get("/api/v1/admin/automation/runs/{runId}", before.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN")))
                .andExpect(status().isOk()).andReturn());
        assertThat(runDetail.at("/snapshots/0/automationSourceId").asLong()).isEqualTo(source.id());
        assertThat(snapshotProvenance(before.id()))
                .as("run %s (%s, %s) snapshot evidence: %s", before.id(), before.status(), before.holdReason(),
                        snapshotProvenance(before.id()))
                .singleElement().satisfies(row ->
                assertThat(row.get("origin_approval_id")).as("snapshot: %s", row)
                        .isEqualTo(approval.get("id").asLong()));
        cancelIfRunning(before);

        automationAdminService.updateSource(source.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS, WIREMOCK.baseUrl() + "/changed-feed.xml", true));
        var invalidated = body(mockMvc.perform(get(
                        "/api/v1/admin/automation/sources/{sourceId}/origin-approvals", source.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN")))
                .andExpect(status().isOk()).andReturn()).get(0);
        assertThat(invalidated.get("active").asBoolean()).isFalse();
        assertThat(invalidated.get("rationale").asText()).isEqualTo("Checked article ownership");
        assertThat(invalidated.get("revocationRationale").asText()).isEqualTo("Source URL or type changed.");
        var after = automationAdminService.triggerManualRun(source.topicId(), "origin-after-source-edit");
        assertThat(snapshotProvenance(after.id())).singleElement().satisfies(row ->
                assertThat(row.get("origin_approval_id")).isNull());
        assertThat(snapshotProvenance(before.id())).singleElement().satisfies(row ->
                assertThat(((Number) row.get("origin_approval_id")).longValue()).isEqualTo(approval.get("id").asLong()));
    }

    private AutomationSourceResponse createSource(String path, AutomationSourceType type) {
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null, "Origin approval " + path, "v1", true));
        return automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                type, WIREMOCK.baseUrl() + path, true));
    }

    private JsonNode createGroup(long topicId) throws Exception {
        return body(mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/origin-groups", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Primary reporting\",\"rationale\":\"Editorial review\"}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode approve(long sourceId, long groupId, String originHost) throws Exception {
        return body(mockMvc.perform(post("/api/v1/admin/automation/sources/{sourceId}/origin-approvals", sourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originHost\":\"%s\",\"groupId\":%d,\"rationale\":\"Checked article ownership\"}"
                                .formatted(originHost, groupId)))
                .andExpect(status().isCreated()).andReturn());
    }

    private void stubFeed(String path, String xml) {
        WIREMOCK.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/rss+xml; charset=utf-8").withBody(xml)));
    }

    private void stubArticle(String path) {
        WIREMOCK.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/html; charset=utf-8")
                .withBody("<html><head><title>Article</title></head><body><article>Independent article evidence.</article></body></html>")));
    }

    private void assertArticleReachable(String url) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).as("Java HTTP probe for %s", url).isEqualTo(200);
        }
        assertThat(pinnedSourceHttpClient.get(sourceUrlPolicy.resolveFetchUrl(url)).statusCode())
                .as("Pinned source HTTP probe for %s", url).isEqualTo(200);
    }

    private List<Map<String, Object>> snapshotProvenance(long runId) {
        return jdbcTemplate.queryForList("""
                SELECT source_url, fetched_url, origin_host, policy_result, http_status, body_excerpt,
                       origin_approval_id, origin_approval_revision, origin_group_id
                FROM source_snapshot WHERE automation_run_id = ? ORDER BY id
                """, runId);
    }

    private void cancelIfRunning(AutomationRunResponse run) {
        if (run.status() == AutomationRunStatus.RUNNING) {
            automationAdminService.cancelRun(run.id());
        }
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearerToken(String role) {
        var claims = JwtClaimsSet.builder()
                .issuer(authProperties.issuer())
                .audience(List.of(authProperties.audience()))
                .subject("1")
                .issuedAt(Instant.now())
                .notBefore(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("username", "admin")
                .claim("roles", List.of(role))
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(RS256).build(), claims)).getTokenValue();
    }
}
