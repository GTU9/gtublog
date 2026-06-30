package com.gtublog.automation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Tag("docker")
@SpringBootTest
class GenerationJobWorkerIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";

    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_generation_worker")
            .withUsername("gtublog")
            .withPassword("gtublog-test-password");

    private static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

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

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AutomationAdminService automationAdminService;

    @Autowired
    private AutomationScheduleSynchronizer automationScheduleSynchronizer;

    @Autowired
    private GenerationJobRepository generationJobRepository;

    @Autowired
    private AutomationRunRepository automationRunRepository;

    @Autowired
    private GenerationJobService generationJobService;

    @Autowired
    private TerminalPayloadDigester terminalPayloadDigester;

    @Autowired
    private AutomationRunRecoveryService automationRunRecoveryService;

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
        jdbcTemplate.update("DELETE FROM post_revision_source_snapshot");
        jdbcTemplate.update("DELETE FROM publication_outbox_event");
        jdbcTemplate.update("DELETE FROM post_revision");
        jdbcTemplate.update("DELETE FROM post");
        jdbcTemplate.update("DELETE FROM source_snapshot");
        jdbcTemplate.update("DELETE FROM generation_job");
        jdbcTemplate.update("DELETE FROM automation_run");
        jdbcTemplate.update("DELETE FROM automation_schedule");
        jdbcTemplate.update("DELETE FROM automation_source");
        jdbcTemplate.update("DELETE FROM automation_topic");
        jdbcTemplate.update("DELETE FROM audit_entry");
    }

    @Test
    void claimRequiresWorkerToken() throws Exception {
        mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v2"]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void workerCanClaimHeartbeatAndSubmitGeneratedDraft() throws Exception {
        var job = seedGenerationJob();

        var claimResult = mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v2"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        var claimJson = jsonBody(claimResult);
        assertThat(claimJson.get("jobId").asLong()).isEqualTo(job.getId());
        assertThat(claimJson.get("providerName").asText()).isEqualTo("fake-provider");
        assertThat(claimJson.get("snapshots")).hasSize(2);
        long firstSnapshotId = claimJson.at("/snapshots/0/snapshotId").asLong();
        long secondSnapshotId = claimJson.at("/snapshots/1/snapshotId").asLong();

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/heartbeat", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successSubmitBody(
                                "worker-a",
                                "prompt-v1",
                                "자동 초안",
                                "요약",
                                "# 자동 초안\n\n본문",
                                java.util.List.of(firstSnapshotId, secondSnapshotId))))
                .andExpect(status().isAccepted());

        var storedJob = generationJobRepository.findById(job.getId()).orElseThrow();
        assertThat(storedJob.getJobStatus()).isEqualTo(GenerationJobStatus.SUBMITTED);
        assertThat(storedJob.getResultPayloadJson()).contains("자동 초안");
        assertThat(storedJob.getSubmittedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_revision", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_revision_source_snapshot", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM publication_outbox_event", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, job.getRunId())).isEqualTo("SUCCEEDED");
    }

    @Test
    void responseLossRetryReturnsCommittedTerminalWithoutRepeatingSideEffects() throws Exception {
        var job = seedGenerationJob();
        var claim = claim("worker-retry");
        var firstSnapshotId = claim.at("/snapshots/0/snapshotId").asLong();
        var secondSnapshotId = claim.at("/snapshots/1/snapshotId").asLong();
        var body = successSubmitBody(
                "worker-retry",
                "prompt-v1",
                "Idempotent draft",
                "Summary",
                "# Idempotent draft\n\nBody",
                java.util.List.of(firstSnapshotId, secondSnapshotId));

        var firstResponse = mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();
        var retryResponse = mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(jsonBody(retryResponse)).isEqualTo(jsonBody(firstResponse));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_revision", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM publication_outbox_event", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_entry WHERE action_type = 'GENERATION_JOB_SUBMITTED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void acceptsThePrecomputedTypeScriptCanonicalDigestFixtureWithoutJavaRewriting() throws Exception {
        var job = seedGenerationJob();
        claim("worker-fixture");
        var fixture = contractFixture("failure-submit-request.json");

        var response = mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture))
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(jsonBody(response).get("payloadDigest").asText())
                .isEqualTo("af7e88f4a9643d9516482d22854cb46f97d1b0119c4aacca2e67a5a085b9f1e1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT terminal_payload_digest FROM generation_job WHERE id = ?",
                String.class,
                job.getId()))
                .isEqualTo("af7e88f4a9643d9516482d22854cb46f97d1b0119c4aacca2e67a5a085b9f1e1");
    }

    @Test
    void rejectsDigestMismatchAndDifferentTerminalAfterSuccess() throws Exception {
        var job = seedGenerationJob();
        var claim = claim("worker-conflict");
        var firstSnapshotId = claim.at("/snapshots/0/snapshotId").asLong();
        var secondSnapshotId = claim.at("/snapshots/1/snapshotId").asLong();
        var successBody = successSubmitBody(
                "worker-conflict",
                "prompt-v1",
                "Committed draft",
                "Summary",
                "# Committed draft\n\nBody",
                java.util.List.of(firstSnapshotId, secondSnapshotId));

        var digestMismatch = (tools.jackson.databind.node.ObjectNode) objectMapper.readTree(successBody);
        digestMismatch.withObject("/draft").put("title", "Tampered after digest");
        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(digestMismatch)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successBody))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failureSubmitBody("worker-conflict", "prompt-v1", "different terminal")))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_entry WHERE action_type = 'GENERATION_JOB_SUBMITTED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsOversizedAndDeeplyInvalidWorkerSubmissionsBeforePublication() throws Exception {
        var oversizedBody = "{\"padding\":\"" + "x".repeat(WorkerRequestSizeFilter.MAX_BODY_BYTES) + "\"}";
        mockMvc.perform(post("/api/v2/internal/generation-jobs/1/submit")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedBody))
                .andExpect(status().isPayloadTooLarge());

        var job = seedGenerationJob();
        claim("worker-invalid");
        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successSubmitBody(
                                "worker-invalid",
                                "prompt-v1",
                                "Invalid citation",
                                "Summary",
                                "Body",
                                java.util.List.of(0L))))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_entry WHERE action_type = 'GENERATION_JOB_SUBMITTED'", Integer.class)).isZero();
    }

    @Test
    void holdsPublicationWhenOnlyOneIndependentSourceIsProvided() throws Exception {
        var job = seedSingleSourceGenerationJob();

        var claimResult = mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v2"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long snapshotId = jsonBody(claimResult).at("/snapshots/0/snapshotId").asLong();

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successSubmitBody(
                                "worker-a",
                                "prompt-v1",
                                "단일 출처 초안",
                                "요약",
                                "# 단일 출처 초안\n\n본문",
                                java.util.List.of(snapshotId))))
                .andExpect(status().isAccepted());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM publication_outbox_event", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, job.getRunId())).isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT hold_reason FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .contains("corroboration");
    }

    @Test
    void rejectsSubmitFromAnotherWorkerOrExpiredLease() throws Exception {
        var job = seedGenerationJob();

        mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v2"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failureSubmitBody("worker-b", "prompt-v1", "other worker")))
                .andExpect(status().isConflict());

        jdbcTemplate.update("UPDATE generation_job SET lease_expires_at = '2026-06-28 00:00:00.000000' WHERE id = ?", job.getId());

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failureSubmitBody("worker-a", "prompt-v1", "lease expired")))
                .andExpect(status().isConflict());
    }

    @Test
    void workerLeaseNeverExtendsPastTheRunDeadline() throws Exception {
        var job = seedGenerationJob();
        jdbcTemplate.update(
                "UPDATE automation_run SET lease_expires_at = UTC_TIMESTAMP(6) + INTERVAL 30 SECOND WHERE id = ?",
                job.getRunId());

        var claimResult = mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v2"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        var runDeadline = jdbcTemplate.queryForObject(
                "SELECT lease_expires_at FROM automation_run WHERE id = ?",
                java.time.LocalDateTime.class,
                job.getRunId());
        assertThat(java.time.Instant.parse(jsonBody(claimResult).get("leaseExpiresAt").asText()))
                .isEqualTo(runDeadline.toInstant(java.time.ZoneOffset.UTC));

        var heartbeatResult = mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/heartbeat", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workerId":"worker-a"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(java.time.Instant.parse(jsonBody(heartbeatResult).get("leaseExpiresAt").asText()))
                .isEqualTo(runDeadline.toInstant(java.time.ZoneOffset.UTC));
        assertThat(java.time.Instant.parse(jsonBody(heartbeatResult).get("serverTime").asText()))
                .isBeforeOrEqualTo(java.time.Instant.parse(jsonBody(heartbeatResult).get("leaseExpiresAt").asText()));
    }

    @Test
    void rejectsLateWorkerSubmissionAfterRunRecovery() throws Exception {
        var job = seedGenerationJob();
        mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v2"]
                                }
                                """))
                .andExpect(status().isOk());
        jdbcTemplate.update(
                "UPDATE automation_run SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                job.getRunId());
        assertThat(automationRunRecoveryService.recoverExpiredRuns()).isEqualTo(1);

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failureSubmitBody("worker-a", "prompt-v1", "late result")))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT job_status FROM generation_job WHERE id = ?", String.class, job.getId())).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM publication_outbox_event", Integer.class)).isZero();
    }

    @Test
    void rejectsSubmissionWhenPromptVersionDoesNotMatchClaimedContract() throws Exception {
        var job = seedGenerationJob();

        mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v2"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failureSubmitBody("worker-a", "unexpected-prompt-version", "provider failed")))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT job_status FROM generation_job WHERE id = ?", String.class, job.getId()))
                .isEqualTo("CLAIMED");
    }

    @Test
    void returnsNoContentWhenNoCompatibleJobExists() throws Exception {
        seedGenerationJob();

        mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "supportedProviders":["codex-sdk"],
                                  "supportedSchemaVersions":["automation-job-v2"]
                                }
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void concurrentWorkersCannotBothClaimTheSameJob() throws Exception {
        var job = seedGenerationJob();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> claimAfterBarrier("worker-a", ready, start));
            var second = executor.submit(() -> claimAfterBarrier("worker-b", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var claims = java.util.stream.Stream.of(
                            first.get(10, TimeUnit.SECONDS),
                            second.get(10, TimeUnit.SECONDS))
                    .filter(java.util.Objects::nonNull)
                    .toList();

            assertThat(claims).singleElement().extracting(GenerationJobClaimResponse::jobId).isEqualTo(job.getId());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_entry WHERE action_type = 'GENERATION_JOB_CLAIMED'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT lease_owner FROM generation_job WHERE id = ?",
                    String.class,
                    job.getId())).isIn("worker-a", "worker-b");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredRunsCannotStarveAnActiveClaimBehindTheCandidatePage() {
        var activeJob = seedGenerationJob();
        var activeRun = automationRunRepository.findById(activeJob.getRunId()).orElseThrow();
        var now = LocalDateTime.now(java.time.Clock.systemUTC());

        for (int index = 0; index < 11; index++) {
            var staleRun = automationRunRepository.saveAndFlush(AutomationRun.start(
                    UUID.randomUUID().toString(),
                    activeRun.getTopicId(),
                    null,
                    "MANUAL",
                    "stale-claim-" + index,
                    "pipeline",
                    now.minusMinutes(1),
                    now.minusHours(2)));
            var staleJob = generationJobRepository.saveAndFlush(GenerationJob.enqueue(
                    UUID.randomUUID().toString(),
                    staleRun.getId(),
                    activeJob.getProviderName(),
                    activeJob.getPromptVersion(),
                    activeJob.getSchemaVersion(),
                    activeJob.getRequestPayloadJson()));
            jdbcTemplate.update(
                    "UPDATE generation_job SET created_at = UTC_TIMESTAMP(6) - INTERVAL 1 DAY WHERE id = ?",
                    staleJob.getId());
        }

        var claim = generationJobService.claim(
                workerToken(),
                new GenerationJobClaimRequest(
                        "worker-active",
                        java.util.List.of("fake-provider"),
                        java.util.List.of("automation-job-v2")));

        assertThat(claim).isNotNull();
        assertThat(claim.jobId()).isEqualTo(activeJob.getId());
    }

    private GenerationJobClaimResponse claimAfterBarrier(
            String workerId,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Claim race did not start in time.");
        }
        return generationJobService.claim(
                workerToken(),
                new GenerationJobClaimRequest(
                        workerId,
                        java.util.List.of("fake-provider"),
                        java.util.List.of("automation-job-v2")));
    }

    private GenerationJob seedGenerationJob() {
        stubAccessibleSource("/worker-feed", "https://example.com/worker-feed");
        stubAccessibleSource("/worker-feed-2", "https://example.org/worker-feed-2");
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Automation Worker",
                "prompt-v1",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML,
                WIREMOCK.baseUrl() + "/worker-feed",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML,
                WIREMOCK.baseUrl().replace("localhost", "127.0.0.1") + "/worker-feed-2",
                true));
        var run = automationAdminService.triggerManualRun(topic.id(), "story-8-run");
        return generationJobRepository.findByRunId(run.id()).orElseThrow();
    }

    private GenerationJob seedSingleSourceGenerationJob() {
        stubAccessibleSource("/worker-feed", "https://example.com/worker-feed");
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Single Source Worker",
                "prompt-v1",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML,
                WIREMOCK.baseUrl() + "/worker-feed",
                true));
        var run = automationAdminService.triggerManualRun(topic.id(), "story-9-held-run");
        return generationJobRepository.findByRunId(run.id()).orElseThrow();
    }

    private String workerToken() {
        return "worker-test-token";
    }

    private JsonNode claim(String workerId) throws Exception {
        var result = mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"%s",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v2"]
                                }
                                """.formatted(workerId)))
                .andExpect(status().isOk())
                .andReturn();
        return jsonBody(result);
    }

    private String successSubmitBody(
            String workerId,
            String promptVersion,
            String title,
            String excerpt,
            String contentMarkdown,
            java.util.List<Long> citationSnapshotIds) throws Exception {
        var requestWithoutDigest = new GenerationJobSubmitRequest(
                UUID.randomUUID().toString(),
                "0".repeat(64),
                workerId,
                "fake-provider",
                promptVersion,
                "automation-job-v2",
                new GenerationJobSubmitRequest.GeneratedDraft(
                        title,
                        excerpt,
                        contentMarkdown,
                        citationSnapshotIds),
                null);
        return objectMapper.writeValueAsString(withCanonicalDigest(requestWithoutDigest));
    }

    private String failureSubmitBody(String workerId, String promptVersion, String failureReason) throws Exception {
        var requestWithoutDigest = new GenerationJobSubmitRequest(
                UUID.randomUUID().toString(),
                "0".repeat(64),
                workerId,
                "fake-provider",
                promptVersion,
                "automation-job-v2",
                null,
                failureReason);
        return objectMapper.writeValueAsString(withCanonicalDigest(requestWithoutDigest));
    }

    private GenerationJobSubmitRequest withCanonicalDigest(GenerationJobSubmitRequest request) {
        return new GenerationJobSubmitRequest(
                request.terminalSubmissionId(),
                terminalPayloadDigester.digest(request),
                request.workerId(),
                request.providerName(),
                request.promptVersion(),
                request.schemaVersion(),
                request.draft(),
                request.failureReason());
    }

    private String contractFixture(String name) throws Exception {
        var root = Path.of(System.getProperty("user.dir"));
        var contracts = root.resolve("contracts");
        if (!Files.isDirectory(contracts)) {
            contracts = root.resolve("..").resolve("contracts").normalize();
        }
        return Files.readString(contracts.resolve("automation/v2/fixtures").resolve(name));
    }

    private void stubAccessibleSource(String path, String canonicalUrl) {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("""
                                <html>
                                  <head>
                                    <title>Worker source</title>
                                    <link rel="canonical" href="%s" />
                                  </head>
                                  <body>
                                    <p>Verified worker content excerpt.</p>
                                  </body>
                                </html>
                                """.formatted(canonicalUrl))));
    }

    private JsonNode jsonBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
