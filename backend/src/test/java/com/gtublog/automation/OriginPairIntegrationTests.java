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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
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
import tools.jackson.databind.node.ObjectNode;

@Tag("docker")
@SpringBootTest(properties = {
        "spring.quartz.auto-startup=false",
        "app.automation.worker.schema-version=automation-job-v3"
})
class OriginPairIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";
    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_origin_pair")
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
    @Autowired private GenerationJobRepository generationJobRepository;
    @Autowired private TerminalPayloadDigester terminalPayloadDigester;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private AuthProperties authProperties;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSource dataSource;
    private MockMvc mockMvc;

    @Autowired
    void configureMockMvc(WebApplicationContext context) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(WorkerRequestSizeFilter.class))
                .apply(springSecurity()).build();
    }

    @BeforeEach
    void resetState() {
        scheduleSynchronizer.clearAutomationSchedules();
        WIREMOCK.resetAll();
        for (var table : List.of(
                "post_view_counter",
                "post_revision_source_snapshot",
                "publication_outbox_event",
                "post_revision",
                "post_tag",
                "post_category",
                "post",
                "source_snapshot",
                "generation_job",
                "automation_run_origin_pair",
                "automation_run",
                "automation_schedule",
                "automation_origin_pair_approval",
                "automation_origin_approval",
                "automation_origin_group",
                "automation_source",
                "automation_topic",
                "tag",
                "category",
                "audit_entry")) {
            deleteIfPresent(table);
        }
    }

    @Test
    void cleanMigrationCreatesOriginPairTablesAndHibernateValidatesThem() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '16' AND success = 1", Integer.class))
                .isEqualTo(1);
        assertThat(columnsFor("automation_origin_pair_approval"))
                .contains(
                        "id",
                        "topic_id",
                        "group_low_id",
                        "group_high_id",
                        "rationale",
                        "revocation_rationale",
                        "active",
                        "revision",
                        "approved_at",
                        "revoked_at",
                        "created_at",
                        "active_slot",
                        "updated_at");
        assertThat(columnsFor("automation_run_origin_pair"))
                .contains(
                        "id",
                        "run_id",
                        "pair_approval_id",
                        "approval_revision",
                        "group_low_id",
                        "group_high_id",
                        "captured_at",
                        "created_at",
                        "updated_at");
        assertThat(uniqueIndexesFor("automation_origin_pair_approval"))
                .contains("uk_origin_pair_active");
        assertThat(uniqueIndexesFor("automation_run_origin_pair"))
                .contains("uk_run_origin_pair_approval");
    }

    @Test
    void onlyAdminCanCreateOriginPairsAndActionsAreAudited() throws Exception {
        var topic = createTopic("pair-auth");
        var firstGroup = createGroup(topic.id(), "Reporter A");
        var secondGroup = createGroup(topic.id(), "Reporter B");
        var path = "/api/v1/admin/automation/topics/" + topic.id() + "/origin-pairs";
        var request = pairRequest(firstGroup.get("id").asLong(), secondGroup.get("id").asLong(), "Editorial independence reviewed");

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_READER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isForbidden());

        var pair = createPair(topic.id(), firstGroup.get("id").asLong(), secondGroup.get("id").asLong(), "Editorial independence reviewed");
        assertThat(pair.get("topicId").asLong()).isEqualTo(topic.id());
        assertThat(pair.get("groupLowId").asLong()).isEqualTo(Math.min(firstGroup.get("id").asLong(), secondGroup.get("id").asLong()));
        assertThat(pair.get("groupHighId").asLong()).isEqualTo(Math.max(firstGroup.get("id").asLong(), secondGroup.get("id").asLong()));
        assertThat(pair.get("groupLowName").asText()).isEqualTo("Reporter A");
        assertThat(pair.get("groupHighName").asText()).isEqualTo("Reporter B");
        assertThat(pair.get("active").asBoolean()).isTrue();
        assertThat(pair.get("revision").asLong()).isEqualTo(1);
        assertThat(pair.get("approvedAt").asText()).isNotBlank();

        var listed = body(mockMvc.perform(get("/api/v1/admin/automation/topics/{topicId}/origin-pairs", topic.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN")))
                .andExpect(status().isOk()).andReturn());
        assertThat(listed.size()).isEqualTo(1);
        assertThat(listed.get(0).get("id").asLong()).isEqualTo(pair.get("id").asLong());
        assertThat(jdbcTemplate.queryForList("""
                SELECT action_type FROM audit_entry
                WHERE action_type IN ('ORIGIN_PAIR_APPROVAL_GRANTED', 'ORIGIN_GROUP_CREATED')
                """, String.class)).contains("ORIGIN_PAIR_APPROVAL_GRANTED", "ORIGIN_GROUP_CREATED");
    }

    @Test
    void rejectsSelfPairAndGroupsOwnedByAnotherTopic() throws Exception {
        var topic = createTopic("pair-validation");
        var group = createGroup(topic.id(), "Same group");
        mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/origin-pairs", topic.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pairRequest(group.get("id").asLong(), group.get("id").asLong(), "Self review"))))
                .andExpect(status().isBadRequest());

        var otherTopic = createTopic("pair-validation-other");
        var first = createGroup(topic.id(), "Local group");
        var other = createGroup(otherTopic.id(), "Other topic group");
        mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/origin-pairs", topic.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pairRequest(first.get("id").asLong(), other.get("id").asLong(), "Wrong topic"))))
                .andExpect(status().isBadRequest());
        assertThat(countRows("automation_origin_pair_approval")).isZero();
    }

    @Test
    void reversedPairIdsAreCanonicalizedAndDuplicateActiveApprovalConflicts() throws Exception {
        var topic = createTopic("pair-canonical");
        var first = createGroup(topic.id(), "First");
        var second = createGroup(topic.id(), "Second");
        var pair = createPair(topic.id(), second.get("id").asLong(), first.get("id").asLong(), "Reversed request");

        assertThat(pair.get("groupLowId").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(pair.get("groupHighId").asLong()).isEqualTo(second.get("id").asLong());
        mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/origin-pairs", topic.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pairRequest(first.get("id").asLong(), second.get("id").asLong(), "Duplicate"))))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentApprovalOfTheSamePairHasOneWinner() throws Exception {
        var topic = createTopic("pair-concurrency");
        var first = createGroup(topic.id(), "Concurrent A");
        var second = createGroup(topic.id(), "Concurrent B");
        var authorization = bearerToken("ROLE_ADMIN");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var request = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent request did not start.");
                return mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/origin-pairs", topic.id())
                                .header(HttpHeaders.AUTHORIZATION, authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(pairRequest(first.get("id").asLong(), second.get("id").asLong(), "Concurrent review"))))
                        .andReturn().getResponse().getStatus();
            };
            var left = executor.submit(request);
            var right = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM automation_origin_pair_approval
                WHERE topic_id = ? AND active = TRUE
                """, Integer.class, topic.id())).isEqualTo(1);
    }

    @Test
    void staleAndRepeatedPairRevokesConflictButCurrentRevisionRevokesAndAudits() throws Exception {
        var topic = createTopic("pair-revoke");
        var first = createGroup(topic.id(), "Revoke A");
        var second = createGroup(topic.id(), "Revoke B");
        var pair = createPair(topic.id(), first.get("id").asLong(), second.get("id").asLong(), "Initial approval");
        var revokePath = "/api/v1/admin/automation/origin-pairs/" + pair.get("id").asLong() + "/revoke";

        mockMvc.perform(post(revokePath)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(revokeRequest(0, "Stale"))))
                .andExpect(status().isConflict());
        var revoked = body(mockMvc.perform(post(revokePath)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(revokeRequest(1, "Ownership changed"))))
                .andExpect(status().isOk()).andReturn());
        assertThat(revoked.get("active").asBoolean()).isFalse();
        assertThat(revoked.get("revision").asLong()).isEqualTo(2);
        assertThat(revoked.get("rationale").asText()).isEqualTo("Initial approval");
        assertThat(revoked.get("revocationRationale").asText()).isEqualTo("Ownership changed");
        assertThat(revoked.get("revokedAt").asText()).isNotBlank();

        mockMvc.perform(post(revokePath)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(revokeRequest(2, "Again"))))
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_entry
                WHERE action_type = 'ORIGIN_PAIR_APPROVAL_REVOKED' AND target_id = ?
                """, Integer.class, Long.toString(pair.get("id").asLong()))).isEqualTo(1);
    }

    @Test
    void runStartCapturesActivePairsImmutablyAndRunReuseDoesNotRefreshCapture() throws Exception {
        var topic = createTopic("pair-capture");
        var first = createGroup(topic.id(), "Capture A");
        var second = createGroup(topic.id(), "Capture B");
        var third = createGroup(topic.id(), "Capture C");
        var originalPair = createPair(topic.id(), first.get("id").asLong(), second.get("id").asLong(), "Original pair");
        var reusedRun = automationAdminService.triggerManualRun(topic.id(), "pair-capture-reuse");

        revokePair(originalPair.get("id").asLong(), 1, "Policy expired");
        var replacementPair = createPair(topic.id(), second.get("id").asLong(), third.get("id").asLong(), "Replacement pair");
        var reusedAgain = automationAdminService.triggerManualRun(topic.id(), "pair-capture-reuse");
        assertThat(reusedAgain.id()).isEqualTo(reusedRun.id());
        assertRunCapturedOnly(reusedRun.id(), originalPair.get("id").asLong(), 1,
                first.get("id").asLong(), second.get("id").asLong());

        var retry = automationAdminService.retryHeldRun(reusedRun.id());
        assertThat(retry.id()).isNotEqualTo(reusedRun.id());
        assertRunCapturedOnly(retry.id(), replacementPair.get("id").asLong(), 1,
                second.get("id").asLong(), third.get("id").asLong());
        assertRunDetailOriginPair(reusedRun.id(), originalPair.get("id").asLong(), 1);
        assertRunDetailOriginPair(retry.id(), replacementPair.get("id").asLong(), 1);
    }

    @Test
    void approvalAndRunStartSerializeOnTopicLock() throws Exception {
        var topic = createTopic("pair-approve-start-race");
        var first = createGroup(topic.id(), "Approve race A");
        var second = createGroup(topic.id(), "Approve race B");

        try (var topicLock = lockTopic(topic.id());
             var executor = Executors.newFixedThreadPool(2)) {
            var approvalStarted = new CountDownLatch(1);
            var approval = executor.submit(() -> {
                approvalStarted.countDown();
                return createPair(topic.id(), first.get("id").asLong(), second.get("id").asLong(), "Queued approval");
            });
            assertThat(approvalStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertStillWaiting(approval);

            var runStarted = new CountDownLatch(1);
            var run = executor.submit(() -> {
                runStarted.countDown();
                return automationAdminService.triggerManualRun(topic.id(), "pair-approve-start-race");
            });
            assertThat(runStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertStillWaiting(run);

            topicLock.commit();
            var pair = approval.get(20, TimeUnit.SECONDS);
            var startedRun = run.get(20, TimeUnit.SECONDS);
            var captures = capturedPairs(startedRun.id());
            assertThat(captures).hasSizeBetween(0, 1);
            if (!captures.isEmpty()) {
                assertRunCapturedOnly(startedRun.id(), pair.get("id").asLong(), 1,
                        first.get("id").asLong(), second.get("id").asLong());
            }
            assertThat(body(mockMvc.perform(get("/api/v1/admin/automation/topics/{topicId}/origin-pairs", topic.id())
                            .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN")))
                    .andExpect(status().isOk()).andReturn()).get(0).get("active").asBoolean()).isTrue();
        }
    }

    @Test
    void revokeAndRunStartSerializeOnTopicLock() throws Exception {
        var topic = createTopic("pair-revoke-start-race");
        var first = createGroup(topic.id(), "Revoke race A");
        var second = createGroup(topic.id(), "Revoke race B");
        var pair = createPair(topic.id(), first.get("id").asLong(), second.get("id").asLong(), "Race approval");

        try (var topicLock = lockTopic(topic.id());
             var executor = Executors.newFixedThreadPool(2)) {
            var revokeStarted = new CountDownLatch(1);
            var revoke = executor.submit(() -> {
                revokeStarted.countDown();
                revokePair(pair.get("id").asLong(), 1, "Queued revoke");
                return null;
            });
            assertThat(revokeStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertStillWaiting(revoke);

            var runStarted = new CountDownLatch(1);
            var run = executor.submit(() -> {
                runStarted.countDown();
                return automationAdminService.triggerManualRun(topic.id(), "pair-revoke-start-race");
            });
            assertThat(runStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertStillWaiting(run);

            topicLock.commit();
            revoke.get(20, TimeUnit.SECONDS);
            var startedRun = run.get(20, TimeUnit.SECONDS);
            var captures = capturedPairs(startedRun.id());
            assertThat(captures).hasSizeBetween(0, 1);
            if (!captures.isEmpty()) {
                assertRunCapturedOnly(startedRun.id(), pair.get("id").asLong(), 1,
                        first.get("id").asLong(), second.get("id").asLong());
            }
            assertThat(body(mockMvc.perform(get("/api/v1/admin/automation/topics/{topicId}/origin-pairs", topic.id())
                            .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN")))
                    .andExpect(status().isOk()).andReturn()).get(0).get("active").asBoolean()).isFalse();
        }
    }

    @Test
    void v3GenerationRemainsHeldEvenWhenOriginPairApprovalExists() throws Exception {
        jdbcTemplate.update("INSERT INTO category (slug, name) VALUES ('technology', 'Technology')");
        jdbcTemplate.update("INSERT INTO tag (slug, name) VALUES ('java', 'Java')");
        long categoryId = jdbcTemplate.queryForObject("SELECT id FROM category WHERE slug = 'technology'", Long.class);
        long tagId = jdbcTemplate.queryForObject("SELECT id FROM tag WHERE slug = 'java'", Long.class);
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null, "Pair held topic", "prompt-v1", true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML, WIREMOCK.baseUrl() + "/pair-held-a", true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML,
                WIREMOCK.baseUrl().replace("localhost", "127.0.0.1") + "/pair-held-b", true));
        stubSource("/pair-held-a", "https://example.com/pair-held-a");
        stubSource("/pair-held-b", "https://example.org/pair-held-b");
        var first = createGroup(topic.id(), "Held A");
        var second = createGroup(topic.id(), "Held B");
        createPair(topic.id(), first.get("id").asLong(), second.get("id").asLong(), "Pair exists");

        long runId = automationAdminService.triggerManualRun(topic.id(), "pair-v3-held-" + UUID.randomUUID()).id();
        var job = generationJobRepository.findByRunId(runId).orElseThrow();
        var claim = claimJob();
        var request = submitRequest(job, claim, categoryId, tagId);
        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isAccepted());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, runId))
                .isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
        assertThat(automationAdminService.runDetail(runId).availableActions().canOverridePublish()).isTrue();
        var detail = body(mockMvc.perform(get("/api/v1/admin/automation/runs/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN")))
                .andExpect(status().isOk()).andReturn());
        assertThat(detail.withArray("originPairs").size()).isEqualTo(1);
    }

    private AutomationTopicResponse createTopic(String slug) {
        return automationAdminService.createTopic(new AutomationTopicRequest(
                slug, "Origin pair " + slug, "v1", true));
    }

    private JsonNode createGroup(long topicId, String name) throws Exception {
        var request = objectMapper.createObjectNode();
        request.put("name", name);
        request.put("rationale", "Editorial group review");
        return body(mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/origin-groups", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode createPair(long topicId, long firstGroupId, long secondGroupId, String rationale) throws Exception {
        return body(mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/origin-pairs", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pairRequest(firstGroupId, secondGroupId, rationale))))
                .andExpect(status().isCreated()).andReturn());
    }

    private void revokePair(long pairId, long revision, String rationale) throws Exception {
        mockMvc.perform(post("/api/v1/admin/automation/origin-pairs/{pairId}/revoke", pairId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(revokeRequest(revision, rationale))))
                .andExpect(status().isOk());
    }

    private ObjectNode pairRequest(long firstGroupId, long secondGroupId, String rationale) {
        var request = objectMapper.createObjectNode();
        request.put("firstGroupId", firstGroupId);
        request.put("secondGroupId", secondGroupId);
        request.put("rationale", rationale);
        return request;
    }

    private ObjectNode revokeRequest(long revision, String rationale) {
        var request = objectMapper.createObjectNode();
        request.put("revision", revision);
        request.put("rationale", rationale);
        return request;
    }

    private JsonNode claimJob() throws Exception {
        var request = objectMapper.createObjectNode();
        request.put("workerId", "pair-worker");
        request.putArray("supportedProviders").add("fake-provider");
        request.putArray("supportedSchemaVersions").add("automation-job-v3");
        return body(mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk()).andReturn());
    }

    private ObjectNode submitRequest(GenerationJob job, JsonNode claim, long categoryId, long tagId) throws Exception {
        var request = objectMapper.createObjectNode();
        request.put("terminalSubmissionId", UUID.randomUUID().toString());
        request.put("payloadDigest", "0".repeat(64));
        request.put("workerId", "pair-worker");
        request.put("providerName", "fake-provider");
        request.put("promptVersion", job.getPromptVersion());
        request.put("schemaVersion", "automation-job-v3");
        var draft = request.putObject("draft");
        draft.put("title", "Pair held generated post");
        draft.put("excerpt", "Structured evidence summary");
        draft.put("contentMarkdown", "Structured paragraph based only on cited snapshots.");
        draft.putArray("citationSnapshotIds")
                .add(claim.at("/snapshots/0/snapshotId").asLong())
                .add(claim.at("/snapshots/1/snapshotId").asLong());
        draft.putObject("taxonomy").put("categoryId", categoryId).putArray("tagIds").add(tagId);
        request.put("payloadDigest", terminalPayloadDigester.digest(
                objectMapper.treeToValue(request, GenerationJobSubmitRequest.class)));
        return request;
    }

    private void assertRunCapturedOnly(long runId, long pairId, long revision, long firstGroupId, long secondGroupId) {
        assertThat(capturedPairs(runId))
                .singleElement()
                .satisfies(row -> {
                    assertThat(((Number) row.get("pair_approval_id")).longValue()).isEqualTo(pairId);
                    assertThat(((Number) row.get("approval_revision")).longValue()).isEqualTo(revision);
                    assertThat(((Number) row.get("group_low_id")).longValue()).isEqualTo(Math.min(firstGroupId, secondGroupId));
                    assertThat(((Number) row.get("group_high_id")).longValue()).isEqualTo(Math.max(firstGroupId, secondGroupId));
                });
    }

    private void assertRunDetailOriginPair(long runId, long pairId, long revision) throws Exception {
        var detail = body(mockMvc.perform(get("/api/v1/admin/automation/runs/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ROLE_ADMIN")))
                .andExpect(status().isOk()).andReturn());
        var pairs = detail.withArray("originPairs");
        assertThat(pairs.size()).isEqualTo(1);
        assertThat(pairs.get(0).get("pairApprovalId").asLong()).isEqualTo(pairId);
        assertThat(pairs.get(0).get("approvalRevision").asLong()).isEqualTo(revision);
    }

    private List<Map<String, Object>> capturedPairs(long runId) {
        return jdbcTemplate.queryForList("""
                SELECT pair_approval_id, approval_revision, group_low_id, group_high_id
                FROM automation_run_origin_pair
                WHERE run_id = ?
                ORDER BY id
                """, runId);
    }

    private java.sql.Connection lockTopic(long topicId) throws Exception {
        var connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        try (var statement = connection.prepareStatement("SELECT id FROM automation_topic WHERE id = ? FOR UPDATE")) {
            statement.setLong(1, topicId);
            assertThat(statement.executeQuery().next()).isTrue();
        }
        return connection;
    }

    private void assertStillWaiting(Future<?> future) throws Exception {
        Thread.sleep(200);
        assertThat(future.isDone()).isFalse();
    }

    private List<String> columnsFor(String tableName) {
        return jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ?
                """, String.class, tableName);
    }

    private List<String> uniqueIndexesFor(String tableName) {
        return jdbcTemplate.query(
                        "SHOW INDEX FROM " + tableName,
                        (resultSet, rowNum) -> new IndexRow(
                                resultSet.getString("Key_name"), resultSet.getLong("Non_unique")))
                .stream()
                .filter(IndexRow::isUnique)
                .map(IndexRow::name)
                .distinct()
                .toList();
    }

    private long countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }

    private void deleteIfPresent(String tableName) {
        var exists = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """, Integer.class, tableName);
        if (exists != null && exists > 0) {
            jdbcTemplate.update("DELETE FROM " + tableName);
        }
    }

    private void stubSource(String path, String canonicalUrl) {
        WIREMOCK.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/html; charset=utf-8")
                .withBody("<html><head><title>Pair source</title><link rel=\"canonical\" href=\""
                        + canonicalUrl + "\" /></head><body><p>Independent article evidence.</p></body></html>")));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
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

    private record IndexRow(String name, long nonUnique) {

        boolean isUnique() {
            return nonUnique == 0;
        }
    }
}
