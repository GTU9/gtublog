package com.gtublog.automation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
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
@SpringBootTest(properties = {
        "spring.quartz.auto-startup=false",
        "app.automation.worker.schema-version=automation-job-v4"
})
class V4StructuredEvidenceIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";
    private static final String LITERAL =
            "Verified structured evidence phrase appears exactly in both sources.";

    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_v4_structured_evidence")
            .withUsername("gtublog")
            .withPassword("gtublog-test-password");

    private static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .dynamicPort().useChunkedTransferEncoding(Options.ChunkedEncodingPolicy.BODY_FILE));

    static {
        MYSQL.start();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/mysql")
                .load()
                .migrate();
    }

    @BeforeAll
    static void startWiremock() {
        WIREMOCK.start();
    }

    @AfterAll
    static void stopWiremock() {
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AutomationAdminService automationAdminService;
    @Autowired private AutomationScheduleSynchronizer automationScheduleSynchronizer;
    @Autowired private GenerationJobRepository generationJobRepository;

    private MockMvc mockMvc;

    @Autowired
    void configureMockMvc(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(WorkerRequestSizeFilter.class))
                .apply(springSecurity())
                .build();
    }

    @BeforeEach
    void resetState() {
        automationScheduleSynchronizer.clearAutomationSchedules();
        WIREMOCK.resetAll();
        deleteIfPresent("automation_v4_observation_diagnostic");
        deleteIfPresent("automation_publication_decision");
        deleteIfPresent("automation_source_relation_diagnostic");
        for (String table : new String[] {
                "post_view_counter", "post_revision_source_snapshot", "publication_outbox_event", "post_revision",
                "post_tag", "post_category", "post", "source_snapshot", "generation_job", "automation_run",
                "automation_schedule", "automation_source", "automation_topic", "tag", "category", "audit_entry"
        }) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @Test
    void claimReturnsV4ObservationContractWithFrozenTaxonomyCatalog() throws Exception {
        var job = seedV4Job("claim-contract");

        var claim = claim("worker-v4-claim");

        assertThat(claim.get("jobId").asLong()).isEqualTo(job.getId());
        assertThat(claim.get("schemaVersion").asText()).isEqualTo("automation-job-v4");
        assertThat(claim.has("taxonomyCatalog")).isTrue();
        assertThat(claim.at("/taxonomyCatalog/categories/0/id").asLong()).isEqualTo(categoryId());
        assertThat(claim.at("/taxonomyCatalog/tags/0/id").asLong()).isEqualTo(tagId());
        assertThat(claim.get("prompt").asText()).contains("SOURCE_MENTION");
        assertThat(claim.get("prompt").asText()).contains("본문 마크다운은 만들지 마세요");
    }

    @Test
    void v4TerminalDigestMatchesWorkerFixtureCanonicalization() throws Exception {
        var fixture = objectMapper.readTree(contractFixture("submit-request.json"));
        var fixtureCanonical = canonicalV4Success(
                fixture.get("terminalSubmissionId").asText(),
                fixture.get("workerId").asText(),
                fixture.get("providerName").asText(),
                fixture.get("promptVersion").asText(),
                fixture.at("/observations/0/literal").asText(),
                List.of(
                        fixture.at("/observations/0/citationSnapshotIds/0").asLong(),
                        fixture.at("/observations/0/citationSnapshotIds/1").asLong()),
                fixture.at("/taxonomy/categoryId").asLong(),
                List.of(fixture.at("/taxonomy/tagIds/0").asLong()));

        assertThat(digest(fixtureCanonical)).isEqualTo(fixture.get("payloadDigest").asText());

        var twoObservationCanonical = canonicalV4Success(
                "55555555-5555-4555-8555-555555555555",
                "worker-fixture",
                "codex-sdk",
                "prompt-v1",
                List.of(
                        observation("First structured evidence mention shared by two source snapshots.", List.of(2L, 1L)),
                        observation("Second structured evidence mention shared by two source snapshots.", List.of(3L, 2L))),
                10L,
                List.of(30L, 20L));
        assertThat(digest(twoObservationCanonical))
                .isEqualTo("33f19a6cda8da294bef329397155e35802844de9719aa916b4dd905102b6d7fb");
    }

    @Test
    void verifiedV4ObservationIsHeldWithoutPostRevisionOrOutbox() throws Exception {
        var job = seedVerifiedV4Job("verified-held");
        var claim = claim("worker-v4-success");
        var snapshotIds = claimSnapshotIds(claim);

        submit(job.getId(), observationBody("worker-v4-success", LITERAL, snapshotIds));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT job_status FROM generation_job WHERE id = ?",
                String.class,
                job.getId())).isEqualTo("SUBMITTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM automation_run WHERE id = ?",
                String.class,
                job.getRunId())).isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_revision", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM publication_outbox_event", Integer.class)).isZero();

        var storedPayload = jdbcTemplate.queryForObject(
                "SELECT result_payload_json FROM generation_job WHERE id = ?",
                String.class,
                job.getId());
        assertThat(storedPayload).contains("\"observations\"");
        assertThat(storedPayload).doesNotContain("contentMarkdown").doesNotContain("\"draft\"");
    }

    @Test
    void verifiedV4ObservationDoesNotExposeLiteralOrAllowManualOverride() throws Exception {
        var job = seedVerifiedV4Job("admin-redaction");
        var claim = claim("worker-v4-admin");

        submit(job.getId(), observationBody("worker-v4-admin", LITERAL, claimSnapshotIds(claim)));

        var detailJson = objectMapper.writeValueAsString(automationAdminService.runDetail(job.getRunId()));
        assertThat(detailJson).doesNotContain(LITERAL);
        assertThat(automationAdminService.runDetail(job.getRunId()).availableActions().canOverridePublish()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unverifiableObservationCases")
    void holdsV4ObservationWhenLiteralCannotBeVerified(String name, Consumer<V4StructuredEvidenceIntegrationTests> arrange,
            String literal, SnapshotSelection snapshots) throws Exception {
        var job = seedVerifiedV4Job(name);
        arrange.accept(this);
        var claim = claim("worker-v4-" + name);

        submit(job.getId(), observationBody("worker-v4-" + name, literal, snapshots.ids(claim)));

        assertHeldWithoutPublication(job);
    }

    @Test
    void rejectsV4ObservationWithUnknownSubmitField() throws Exception {
        var job = seedVerifiedV4Job("unknown-field");
        var claim = claim("worker-v4-unknown");
        var body = objectMapper.readValue(
                observationBody("worker-v4-unknown", LITERAL, claimSnapshotIds(claim)),
                Map.class);
        body.put("extra", true);

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertNoTerminalSideEffects(job);
    }

    @Test
    void rejectsV4ObservationWithDraftOrMarkdownFields() throws Exception {
        var job = seedVerifiedV4Job("draft-rejected");
        claim("worker-v4-draft");

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "terminalSubmissionId":"%s",
                                  "payloadDigest":"%s",
                                  "workerId":"worker-v4-draft",
                                  "providerName":"fake-provider",
                                  "promptVersion":"prompt-v1",
                                  "schemaVersion":"automation-job-v4",
                                  "draft":{
                                    "title":"Not allowed",
                                    "excerpt":"Not allowed",
                                    "contentMarkdown":"# Not allowed",
                                    "citationSnapshotIds":[1]
                                  }
                                }
                                """.formatted(UUID.randomUUID(), "0".repeat(64))))
                .andExpect(status().isBadRequest());

        assertNoTerminalSideEffects(job);
    }

    @Test
    void holdsV4ObservationWhenTaxonomySelectionWasNotInFrozenCatalog() throws Exception {
        var job = seedVerifiedV4Job("taxonomy-outside-catalog");
        var claim = claim("worker-v4-taxonomy");

        submit(job.getId(), observationBody(
                "worker-v4-taxonomy",
                LITERAL,
                claimSnapshotIds(claim),
                categoryId() + 1000,
                List.of(tagId())));

        assertHeldWithoutPublication(job);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT hold_reason FROM automation_run WHERE id = ?",
                String.class,
                job.getRunId())).isEqualTo(AutomationHoldReason.TAXONOMY_SELECTION_OUTSIDE_CATALOG);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT detail_reason FROM automation_publication_decision WHERE run_id = ?",
                String.class,
                job.getRunId())).isEqualTo("TAXONOMY_INVALID");
    }

    @Test
    void rejectsV4ObservationWithDuplicateCitationSnapshotIdAsShapeViolation() throws Exception {
        var job = seedVerifiedV4Job("duplicate-snapshot");
        var claim = claim("worker-v4-duplicate");
        long firstSnapshotId = claim.at("/snapshots/0/snapshotId").asLong();

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(observationBody("worker-v4-duplicate", LITERAL, List.of(firstSnapshotId, firstSnapshotId))))
                .andExpect(status().isBadRequest());

        assertNoTerminalSideEffects(job);
    }

    @Test
    void rejectsV4ObservationWhenDraftFieldIsPresentEvenNull() throws Exception {
        var job = seedVerifiedV4Job("forbidden-null-success-fields");
        var claim = claim("worker-v4-null-success-fields");
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) objectMapper.readValue(
                observationBody("worker-v4-null-success-fields", LITERAL, claimSnapshotIds(claim)),
                Map.class);
        body.put("draft", null);

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertNoTerminalSideEffects(job);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("forbiddenNullFailureFields")
    void rejectsV4FailureWhenForbiddenNullSuccessFieldIsPresent(String fieldName) throws Exception {
        var job = seedVerifiedV4Job("forbidden-null-failure-" + fieldName);
        claim("worker-v4-null-failure-fields");
        var body = new LinkedHashMap<String, Object>();
        body.put("terminalSubmissionId", UUID.randomUUID().toString());
        body.put("payloadDigest", "0".repeat(64));
        body.put("workerId", "worker-v4-null-failure-fields");
        body.put("providerName", "fake-provider");
        body.put("promptVersion", "prompt-v1");
        body.put("schemaVersion", "automation-job-v4");
        body.put("failureReason", "worker failed before producing structured observations");
        body.put(fieldName, null);

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertNoTerminalSideEffects(job);
    }

    @Test
    void rejectsV4ObservationWithNullCitationSnapshotIdWithoutServerError() throws Exception {
        var job = seedVerifiedV4Job("null-citation");
        var claim = claim("worker-v4-null-citation");
        long firstSnapshotId = claim.at("/snapshots/0/snapshotId").asLong();

        var result = mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(observationBodyWithRawCitationIds(
                                "worker-v4-null-citation",
                                LITERAL,
                                java.util.Arrays.asList(null, firstSnapshotId))))
                .andReturn();

        assertBadRequestOrHeldWithoutPublication(result, job);
    }

    @Test
    void rejectsV4ObservationWithFormatControlLiteralWithoutServerError() throws Exception {
        var job = seedVerifiedV4Job("format-control-literal");
        var claim = claim("worker-v4-format-control");

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(observationBody(
                                "worker-v4-format-control",
                                "Verified structured \u200Bevidence phrase \u202Eappears exactly in both sources.",
                                claimSnapshotIds(claim))))
                .andExpect(status().isBadRequest());

        assertNoTerminalSideEffects(job);
    }

    @Test
    void rejectsV4ObservationWithMarkupLiteralWithoutServerError() throws Exception {
        var job = seedVerifiedV4Job("markup-literal");
        var claim = claim("worker-v4-markup");

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(observationBody(
                                "worker-v4-markup",
                                "Verified structured <tag>evidence</tag> phrase appears exactly in both sources.",
                                claimSnapshotIds(claim))))
                .andExpect(status().isBadRequest());

        assertNoTerminalSideEffects(job);
    }

    @Test
    void replayingSameV4TerminalPayloadReturnsCommittedResultWithoutDuplicatingJudgment() throws Exception {
        var job = seedVerifiedV4Job("terminal-replay");
        var claim = claim("worker-v4-replay");
        var body = observationBody("worker-v4-replay", LITERAL, claimSnapshotIds(claim));

        var first = submit(job.getId(), body);
        var retry = submit(job.getId(), body);

        assertThat(jsonBody(retry)).isEqualTo(jsonBody(first));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_entry WHERE action_type = 'GENERATION_JOB_SUBMITTED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
    }

    @Test
    void rejectsV4DigestMismatchBeforeCommittingTerminalPayload() throws Exception {
        var job = seedVerifiedV4Job("digest-mismatch");
        var claim = claim("worker-v4-digest");
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) objectMapper.readValue(
                observationBody("worker-v4-digest", LITERAL, claimSnapshotIds(claim)),
                Map.class);
        body.put("payloadDigest", "f".repeat(64));

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());

        assertNoTerminalSideEffects(job);
    }

    @Test
    void rejectsV4SubmitAfterLeaseExpiry() throws Exception {
        var job = seedVerifiedV4Job("lease-expired");
        var claim = claim("worker-v4-lease");
        jdbcTemplate.update(
                "UPDATE generation_job SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                job.getId());

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(observationBody("worker-v4-lease", LITERAL, claimSnapshotIds(claim))))
                .andExpect(status().isConflict());

        assertNoTerminalSideEffects(job);
    }

    @Test
    void v3DraftSubmissionStillUsesLegacyHeldPath() throws Exception {
        var job = seedVerifiedV4Job("v3-unchanged");
        jdbcTemplate.update(
                "UPDATE generation_job SET schema_version = 'automation-job-v3' WHERE id = ?",
                job.getId());
        var claim = claim("worker-v3-legacy", "automation-job-v3");
        var snapshotIds = claimSnapshotIds(claim);

        submit(job.getId(), v3DraftBody("worker-v3-legacy", snapshotIds));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM automation_run WHERE id = ?",
                String.class,
                job.getRunId())).isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT hold_reason FROM automation_run WHERE id = ?",
                String.class,
                job.getRunId())).isEqualTo(AutomationHoldReason.INSUFFICIENT_ORIGINS);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT result_payload_json FROM generation_job WHERE id = ?",
                String.class,
                job.getId())).contains("\"contentMarkdown\"").doesNotContain("\"observations\"");
        assertThat(automationAdminService.runDetail(job.getRunId()).availableActions().canOverridePublish()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
    }

    private static Stream<Arguments> unverifiableObservationCases() {
        return Stream.of(
                Arguments.of("other-run", mutateOtherRun(), LITERAL, SnapshotSelection.FIRST_TWO),
                Arguments.of("blocked-source", updateFirstSnapshot("policy_result = 'HELD'"), LITERAL, SnapshotSelection.FIRST_TWO),
                Arguments.of("missing-evidence", updateFirstSnapshot("article_evidence_text = NULL, article_evidence_hash = NULL"), LITERAL, SnapshotSelection.FIRST_TWO),
                Arguments.of("truncated-evidence", updateFirstSnapshot("article_evidence_truncated = TRUE"), LITERAL, SnapshotSelection.FIRST_TWO),
                Arguments.of("hash-mismatch", updateFirstSnapshot("article_evidence_hash = '" + "a".repeat(64) + "'"), LITERAL, SnapshotSelection.FIRST_TWO),
                Arguments.of("partial-word", noMutation(), "structured evidence phrase", SnapshotSelection.FIRST_TWO),
                Arguments.of("ambiguous-observation", duplicateLiteralInFirstSnapshot(), LITERAL, SnapshotSelection.FIRST_TWO)
        );
    }

    private static Stream<String> forbiddenNullFailureFields() {
        return Stream.of("draft", "observations", "taxonomy");
    }

    private GenerationJob seedVerifiedV4Job(String name) {
        var job = seedV4Job(name);
        var snapshotIds = snapshotIds(job.getRunId());
        updateEvidence(snapshotIds.get(0), "First source prefix. " + LITERAL + " First source suffix.");
        updateEvidence(snapshotIds.get(1), "Second source prefix. " + LITERAL + " Second source suffix.");
        return job;
    }

    private GenerationJob seedV4Job(String name) {
        jdbcTemplate.update("INSERT IGNORE INTO category (slug, name) VALUES (?, ?)", "technology", "Technology");
        jdbcTemplate.update("INSERT IGNORE INTO tag (slug, name) VALUES (?, ?)", "java", "Java");
        stubArticle("/" + name + "-a", "https://source-a.example/" + name, "A " + name);
        stubArticle("/" + name + "-b", "https://source-b.example/" + name, "B " + name);
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Story 32 " + name,
                "prompt-v1",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML,
                WIREMOCK.baseUrl() + "/" + name + "-a",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML,
                WIREMOCK.baseUrl().replace("localhost", "127.0.0.1") + "/" + name + "-b",
                true));
        var run = automationAdminService.triggerManualRun(topic.id(), "story-32-" + name + "-" + UUID.randomUUID());
        return generationJobRepository.findByRunId(run.id())
                .orElseThrow(() -> new AssertionError("Expected v4 generation job for run " + run.id()
                        + " but run was " + run.status() + " with hold " + run.holdReason()));
    }

    private JsonNode claim(String workerId) throws Exception {
        return claim(workerId, "automation-job-v4");
    }

    private JsonNode claim(String workerId, String schemaVersion) throws Exception {
        var result = mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"%s",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["%s"]
                                }
                                """.formatted(workerId, schemaVersion)))
                .andExpect(status().isOk())
                .andReturn();
        return jsonBody(result);
    }

    private MvcResult submit(Long jobId, String body) throws Exception {
        return mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", jobId)
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();
    }

    private String observationBody(String workerId, String literal, List<Long> citationSnapshotIds) throws Exception {
        return observationBody(workerId, literal, citationSnapshotIds, categoryId(), List.of(tagId()));
    }

    private String observationBody(
            String workerId,
            String literal,
            List<Long> citationSnapshotIds,
            Long categoryId,
            List<Long> tagIds) throws Exception {
        var terminalSubmissionId = UUID.randomUUID().toString();
        var canonical = canonicalV4Success(
                terminalSubmissionId,
                workerId,
                literal,
                citationSnapshotIds,
                categoryId,
                tagIds);
        var body = new LinkedHashMap<>(canonical);
        body.put("payloadDigest", digest(canonical));
        body.remove("draft");
        body.remove("failureReason");
        return objectMapper.writeValueAsString(body);
    }

    private String observationBodyWithRawCitationIds(
            String workerId,
            String literal,
            List<Long> citationSnapshotIds) throws Exception {
        var terminalSubmissionId = UUID.randomUUID().toString();
        var value = new LinkedHashMap<String, Object>();
        value.put("terminalSubmissionId", terminalSubmissionId);
        value.put("workerId", workerId);
        value.put("providerName", "fake-provider");
        value.put("promptVersion", "prompt-v1");
        value.put("schemaVersion", "automation-job-v4");
        value.put("draft", null);
        var observation = new LinkedHashMap<String, Object>();
        observation.put("kind", "SOURCE_MENTION");
        observation.put("literal", normalizeEvidenceText(literal));
        observation.put("citationSnapshotIds", citationSnapshotIds);
        value.put("observations", List.of(observation));
        value.put("taxonomy", taxonomy(categoryId(), List.of(tagId())));
        value.put("failureReason", null);
        var body = new LinkedHashMap<>(value);
        body.put("payloadDigest", digest(value));
        body.remove("draft");
        body.remove("failureReason");
        return objectMapper.writeValueAsString(body);
    }

    private String v3DraftBody(String workerId, List<Long> citationSnapshotIds) throws Exception {
        var terminalSubmissionId = UUID.randomUUID().toString();
        var canonical = canonicalV3Success(terminalSubmissionId, workerId, citationSnapshotIds);
        var body = new LinkedHashMap<>(canonical);
        body.put("payloadDigest", digest(canonical));
        return objectMapper.writeValueAsString(body);
    }

    private LinkedHashMap<String, Object> canonicalV3Success(
            String terminalSubmissionId,
            String workerId,
            List<Long> citationSnapshotIds) {
        var value = new LinkedHashMap<String, Object>();
        value.put("terminalSubmissionId", terminalSubmissionId);
        value.put("workerId", workerId);
        value.put("providerName", "fake-provider");
        value.put("promptVersion", "prompt-v1");
        value.put("schemaVersion", "automation-job-v3");
        var draft = new LinkedHashMap<String, Object>();
        draft.put("title", "Legacy v3 draft");
        draft.put("excerpt", "Legacy v3 excerpt");
        draft.put("contentMarkdown", "# Legacy v3 draft\n\nBody remains freeform.");
        draft.put("citationSnapshotIds", citationSnapshotIds.stream().sorted().toList());
        draft.put("taxonomy", taxonomy(categoryId(), List.of(tagId())));
        value.put("draft", draft);
        value.put("failureReason", null);
        return value;
    }

    private LinkedHashMap<String, Object> canonicalV4Success(
            String terminalSubmissionId,
            String workerId,
            String literal,
            List<Long> citationSnapshotIds,
            Long categoryId,
            List<Long> tagIds) {
        return canonicalV4Success(
                terminalSubmissionId,
                workerId,
                "fake-provider",
                "prompt-v1",
                List.of(observation(literal, citationSnapshotIds)),
                categoryId,
                tagIds);
    }

    private LinkedHashMap<String, Object> canonicalV4Success(
            String terminalSubmissionId,
            String workerId,
            String providerName,
            String promptVersion,
            String literal,
            List<Long> citationSnapshotIds,
            Long categoryId,
            List<Long> tagIds) {
        return canonicalV4Success(
                terminalSubmissionId,
                workerId,
                providerName,
                promptVersion,
                List.of(observation(literal, citationSnapshotIds)),
                categoryId,
                tagIds);
    }

    private LinkedHashMap<String, Object> canonicalV4Success(
            String terminalSubmissionId,
            String workerId,
            String providerName,
            String promptVersion,
            List<LinkedHashMap<String, Object>> observations,
            Long categoryId,
            List<Long> tagIds) {
        var value = new LinkedHashMap<String, Object>();
        value.put("terminalSubmissionId", terminalSubmissionId);
        value.put("workerId", workerId);
        value.put("providerName", providerName);
        value.put("promptVersion", promptVersion);
        value.put("schemaVersion", "automation-job-v4");
        value.put("draft", null);
        value.put("observations", observations);
        value.put("taxonomy", taxonomy(categoryId, tagIds));
        value.put("failureReason", null);
        return value;
    }

    private LinkedHashMap<String, Object> observation(String literal, List<Long> citationSnapshotIds) {
        var observation = new LinkedHashMap<String, Object>();
        observation.put("kind", "SOURCE_MENTION");
        observation.put("literal", normalizeEvidenceText(literal));
        observation.put("citationSnapshotIds", citationSnapshotIds.stream().sorted().toList());
        return observation;
    }

    private LinkedHashMap<String, Object> taxonomy(Long categoryId, List<Long> tagIds) {
        var taxonomy = new LinkedHashMap<String, Object>();
        taxonomy.put("categoryId", categoryId);
        taxonomy.put("tagIds", tagIds.stream().sorted().toList());
        return taxonomy;
    }

    private List<Long> claimSnapshotIds(JsonNode claim) {
        return List.of(
                claim.at("/snapshots/0/snapshotId").asLong(),
                claim.at("/snapshots/1/snapshotId").asLong());
    }

    private List<Long> snapshotIds(Long runId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM source_snapshot WHERE automation_run_id = ? ORDER BY id",
                Long.class,
                runId);
    }

    private void updateEvidence(Long snapshotId, String evidenceText) {
        jdbcTemplate.update(
                """
                UPDATE source_snapshot
                SET article_evidence_text = ?,
                    article_evidence_hash = ?,
                    article_evidence_truncated = FALSE
                WHERE id = ?
                """,
                evidenceText,
                sha256(evidenceText),
                snapshotId);
    }

    private static Consumer<V4StructuredEvidenceIntegrationTests> updateFirstSnapshot(String setClause) {
        return test -> {
            var runId = test.latestRunId();
            var firstSnapshotId = test.snapshotIds(runId).get(0);
            test.jdbcTemplate.update("UPDATE source_snapshot SET " + setClause + " WHERE id = ?", firstSnapshotId);
        };
    }

    private static Consumer<V4StructuredEvidenceIntegrationTests> mutateOtherRun() {
        return test -> {
            var firstSnapshotId = test.snapshotIds(test.latestRunId()).get(0);
            var otherRun = test.seedVerifiedV4Job("other-run-source");
            test.jdbcTemplate.update(
                    "UPDATE source_snapshot SET automation_run_id = ? WHERE id = ?",
                    otherRun.getRunId(),
                    firstSnapshotId);
        };
    }

    private static Consumer<V4StructuredEvidenceIntegrationTests> duplicateLiteralInFirstSnapshot() {
        return test -> {
            var firstSnapshotId = test.snapshotIds(test.latestRunId()).get(0);
            test.updateEvidence(firstSnapshotId, LITERAL + " Middle text. " + LITERAL);
        };
    }

    private static Consumer<V4StructuredEvidenceIntegrationTests> noMutation() {
        return test -> {
        };
    }

    private Long latestRunId() {
        return jdbcTemplate.queryForObject("SELECT id FROM automation_run ORDER BY id DESC LIMIT 1", Long.class);
    }

    private void assertNoTerminalSideEffects(GenerationJob job) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT job_status FROM generation_job WHERE id = ?",
                String.class,
                job.getId())).isEqualTo("CLAIMED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT terminal_submission_id FROM generation_job WHERE id = ?",
                String.class,
                job.getId())).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_revision", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM publication_outbox_event", Integer.class)).isZero();
    }

    private void assertHeldWithoutPublication(GenerationJob job) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT job_status FROM generation_job WHERE id = ?",
                String.class,
                job.getId())).isEqualTo("SUBMITTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM automation_run WHERE id = ?",
                String.class,
                job.getRunId())).isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_revision", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM publication_outbox_event", Integer.class)).isZero();
    }

    private void assertBadRequestOrHeldWithoutPublication(MvcResult result, GenerationJob job) {
        int statusCode = result.getResponse().getStatus();
        assertThat(statusCode).isIn(400, 202);
        if (statusCode == 400) {
            assertNoTerminalSideEffects(job);
        } else {
            assertHeldWithoutPublication(job);
        }
    }

    private long categoryId() {
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE slug = 'technology'", Long.class);
    }

    private long tagId() {
        return jdbcTemplate.queryForObject("SELECT id FROM tag WHERE slug = 'java'", Long.class);
    }

    private String digest(Map<String, Object> canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(canonical)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String normalizeEvidenceText(String value) {
        var normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        var builder = new StringBuilder();
        boolean previousWasSpace = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            if (Character.isWhitespace(codePoint)
                    || Character.getType(codePoint) == Character.SPACE_SEPARATOR
                    || codePoint == 0x0085) {
                previousWasSpace = true;
            } else {
                if (previousWasSpace && !builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.appendCodePoint(codePoint);
                previousWasSpace = false;
            }
            offset += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private String workerToken() {
        return "worker-test-token";
    }

    private String contractFixture(String name) throws Exception {
        var root = Path.of(System.getProperty("user.dir"));
        var contracts = root.resolve("contracts");
        if (!Files.isDirectory(contracts)) {
            contracts = root.resolve("..").resolve("contracts").normalize();
        }
        return Files.readString(contracts.resolve("automation/v4/fixtures").resolve(name));
    }

    private void stubArticle(String path, String canonicalUrl, String bodyText) {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("""
                                <html>
                                  <head>
                                    <title>Story 32 source</title>
                                    <link rel="canonical" href="%s" />
                                  </head>
                                  <body>
                                    <article><p>%s</p></article>
                                  </body>
                                </html>
                                """.formatted(canonicalUrl, bodyText))));
    }

    private JsonNode jsonBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void deleteIfPresent(String tableName) {
        try {
            jdbcTemplate.update("DELETE FROM " + tableName);
        } catch (BadSqlGrammarException ignored) {
        }
    }

    private enum SnapshotSelection {
        FIRST_TWO {
            @Override
            List<Long> ids(JsonNode claim) {
                return List.of(claim.at("/snapshots/0/snapshotId").asLong(), claim.at("/snapshots/1/snapshotId").asLong());
            }
        };

        abstract List<Long> ids(JsonNode claim);
    }
}
