package com.gtublog.automation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
import org.quartz.Scheduler;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@Tag("docker")
@SpringBootTest
class AutomationPipelineIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";

    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_automation")
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
    private JwtEncoder jwtEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.gtublog.auth.AuthProperties authProperties;

    @Autowired
    private AutomationAdminService automationAdminService;

    @Autowired
    private AutomationScheduleSynchronizer automationScheduleSynchronizer;

    @Autowired
    private AutomationRunLifecycleService automationRunLifecycleService;

    @Autowired
    private AutomationRunRecoveryService automationRunRecoveryService;

    @Autowired
    private GenerationJobService generationJobService;

    @Autowired
    private AutomationTopicRepository automationTopicRepository;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    void configureMockMvc(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @BeforeEach
    void resetState() {
        automationScheduleSynchronizer.clearAutomationSchedules();
        WIREMOCK.resetAll();
        jdbcTemplate.update("DELETE FROM source_snapshot");
        jdbcTemplate.update("DELETE FROM generation_job");
        jdbcTemplate.update("DELETE FROM automation_run");
        jdbcTemplate.update("DELETE FROM automation_schedule");
        jdbcTemplate.update("DELETE FROM automation_source");
        jdbcTemplate.update("DELETE FROM automation_topic");
        jdbcTemplate.update("DELETE FROM audit_entry");
    }

    @Test
    void automationAdminEndpointsRequireAdminJwt() throws Exception {
        mockMvc.perform(get("/api/v1/admin/automation/topics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quartzUsesTheClusteredPersistentJdbcJobStore() throws Exception {
        var metadata = scheduler.getMetaData();
        assertThat(metadata.isJobStoreSupportsPersistence()).isTrue();
        assertThat(metadata.isJobStoreClustered()).isTrue();
        assertThat(scheduler.checkExists(org.quartz.TriggerKey.triggerKey(
                "expired-run-recovery", "automation-maintenance"))).isTrue();
    }

    @Test
    void recoversAnExpiredPipelineRunExactlyOnce() throws Exception {
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(null, "Recovery Topic", "v1", false));
        var run = automationRunLifecycleService.start(topic.id(), null, "MANUAL", "expired-orphan-run").run();
        jdbcTemplate.update(
                "UPDATE automation_run SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                run.getId());

        assertThat(automationRunRecoveryService.recoverExpiredRuns()).isEqualTo(1);
        assertThat(automationRunRecoveryService.recoverExpiredRuns()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM automation_run WHERE id = ?", String.class, run.getId())).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT lease_expires_at IS NULL FROM automation_run WHERE id = ?", Boolean.class, run.getId())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_entry WHERE target_id = ? AND action_type = 'AUTOMATION_RUN_RECOVERED_AS_FAILED'",
                Integer.class,
                run.getId().toString())).isEqualTo(1);
    }

    @Test
    void recoveryCancelsThePendingJobAndPreventsLateClaims() throws Exception {
        stubAccessibleSource("/recovery-feed");
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(null, "Pending Recovery", "v1", false));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML,
                WIREMOCK.baseUrl() + "/recovery-feed",
                true));
        var run = automationAdminService.triggerManualRun(topic.id(), "expired-pending-run");
        jdbcTemplate.update(
                "UPDATE automation_run SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                run.id());

        assertThat(automationRunRecoveryService.recoverExpiredRuns()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM automation_run WHERE id = ?", String.class, run.id())).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT job_status FROM generation_job WHERE run_id = ?", String.class, run.id())).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM publication_outbox_event", Integer.class)).isZero();
    }

    @Test
    void concurrentRecoveryCreatesOneTerminalTransitionAndOneAuditEvent() throws Exception {
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(null, "Concurrent Recovery", "v1", false));
        var run = automationRunLifecycleService.start(topic.id(), null, "MANUAL", "concurrent-expired-run").run();
        jdbcTemplate.update(
                "UPDATE automation_run SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                run.getId());

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> recover = () -> automationRunRecoveryService.recoverExpiredRuns();
            var results = executor.invokeAll(List.of(recover, recover));
            assertThat(results.get(0).get() + results.get(1).get()).isEqualTo(1);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_entry WHERE target_id = ? AND action_type = 'AUTOMATION_RUN_RECOVERED_AS_FAILED'",
                Integer.class,
                run.getId().toString())).isEqualTo(1);
    }

    @Test
    void recoveryCannotBeOverwrittenByAConcurrentHold() throws Exception {
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(null, "Hold Race", "v1", false));
        var run = automationRunLifecycleService.start(topic.id(), null, "MANUAL", "hold-race-run").run();
        jdbcTemplate.update(
                "UPDATE automation_run SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                run.getId());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var recovery = executor.submit(() -> automationRunRecoveryService.recoverExpiredRuns());
            var hold = executor.submit(() -> {
                try {
                    automationRunLifecycleService.hold(run.getId(), "late hold");
                    return false;
                } catch (IllegalStateException expected) {
                    return true;
                }
            });
            assertThat(recovery.get()).isEqualTo(1);
            assertThat(hold.get()).isTrue();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM automation_run WHERE id = ?", String.class, run.getId())).isEqualTo("FAILED");
    }

    @Test
    void recoveryAndEnqueueUseOneLockOrderWithoutLeavingAnOrphanJob() throws Exception {
        var topicResponse = automationAdminService.createTopic(new AutomationTopicRequest(null, "Enqueue Race", "v1", false));
        var topic = automationTopicRepository.findById(topicResponse.id()).orElseThrow();
        var run = automationRunLifecycleService.start(topic.getId(), null, "MANUAL", "enqueue-race-run").run();
        jdbcTemplate.update(
                "UPDATE automation_run SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                run.getId());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var enqueue = executor.submit(() -> {
                try {
                    generationJobService.enqueueForRun(topic, run, List.of());
                    return false;
                } catch (IllegalStateException expected) {
                    return true;
                }
            });
            var recovery = executor.submit(() -> automationRunRecoveryService.recoverExpiredRuns());
            assertThat(enqueue.get()).isTrue();
            assertThat(recovery.get()).isEqualTo(1);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM generation_job WHERE run_id = ?", Integer.class, run.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM automation_run WHERE id = ?", String.class, run.getId())).isEqualTo("FAILED");
    }

    @Test
    void rejectsPrivateSourceAddressesAndBlocksRedirectsToMetadataServices() {
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Security Topic",
                "v1",
                false));

        assertThatThrownBy(() -> automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                        AutomationSourceType.HTML,
                        "http://169.254.169.254/latest/meta-data",
                        true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private network");

        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/redirect-to-metadata"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "http://169.254.169.254/latest/meta-data")));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML,
                WIREMOCK.baseUrl() + "/redirect-to-metadata",
                true));

        var run = automationAdminService.triggerManualRun(topic.id(), "security-redirect-run");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT policy_result FROM source_snapshot WHERE automation_run_id = ?",
                String.class,
                run.id())).isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT body_excerpt FROM source_snapshot WHERE automation_run_id = ?",
                String.class,
                run.id())).contains("private network");
    }

    @Test
    void supportsTopicSourceScheduleCrudAndManualRunCollection() throws Exception {
        stubAccessibleSource("/topic-feed");
        var bearerToken = bearerToken();

        long topicId = jsonBody(mockMvc.perform(post("/api/v1/admin/automation/topics")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"AI Daily",
                                  "promptTemplateVersion":"v1",
                                  "publicationEnabled":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/sources", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType":"HTML",
                                  "sourceUrl":"%s",
                                  "enabled":true
                                }
                                """.formatted(WIREMOCK.baseUrl() + "/topic-feed")))
                .andExpect(status().isCreated());

        var scheduleResult = mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/schedules", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Morning crawl",
                                  "cronExpression":"0 0 8 * * *",
                                  "timezone":"Asia/Seoul",
                                  "status":"ACTIVE",
                                  "misfirePolicy":"FIRE_ONCE_NOW"
                                }
                                """))
                .andReturn();
        assertThat(scheduleResult.getResponse().getStatus()).isEqualTo(201);
        assertThat(jsonBody(scheduleResult).get("nextPlannedRunAt").asText()).isNotBlank();
        assertThat(jsonBody(scheduleResult).get("syncStatus").asText()).isEqualTo("SYNCED");
        assertThat(jsonBody(scheduleResult).get("lastSynchronizedAt").asText()).isNotBlank();

        var runResult = mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/runs/manual", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"story-7-manual-run"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long runId = jsonBody(runResult).get("id").asLong();
        assertThat(jsonBody(runResult).get("status").asText()).isEqualTo("RUNNING");
        assertThat(jsonBody(runResult).get("snapshotCount").asLong()).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/admin/automation/runs/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var json = jsonBody(result);
                    assertThat(json.at("/run/triggerType").asText()).isEqualTo("MANUAL");
                    assertThat(json.at("/run/status").asText()).isEqualTo("RUNNING");
                    assertThat(json.at("/snapshots/0/policyResult").asText()).isEqualTo("ALLOWED");
                    assertThat(json.at("/snapshots/0/sourceUrl").asText()).contains("/topic-feed");
                });
    }

    @Test
    void preventsDeletingReferencedSourcesAndSchedulesAndReturnsConflictDetails() throws Exception {
        stubAccessibleSource("/reference-feed");
        var bearerToken = bearerToken();

        long topicId = jsonBody(mockMvc.perform(post("/api/v1/admin/automation/topics")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Protected Topic",
                                  "promptTemplateVersion":"v1",
                                  "publicationEnabled":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        long sourceId = jsonBody(mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/sources", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType":"HTML",
                                  "sourceUrl":"%s",
                                  "enabled":true
                                }
                                """.formatted(WIREMOCK.baseUrl() + "/reference-feed")))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        long scheduleId = jsonBody(mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/schedules", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Protected schedule",
                                  "cronExpression":"0 30 9 * * *",
                                  "timezone":"Asia/Seoul",
                                  "status":"ACTIVE",
                                  "misfirePolicy":"DO_NOTHING"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        var run = automationAdminService.triggerManualRun(topicId, "protected-run");
        automationAdminService.triggerScheduledRun(scheduleId, "protected-scheduled-run", Instant.parse("2026-06-30T01:00:00Z"));

        assertThat(run.snapshotCount()).isGreaterThan(0);

        mockMvc.perform(delete("/api/v1/admin/automation/sources/{sourceId}", sourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isConflict())
                .andExpect(result -> assertThat(jsonBody(result).get("detail").asText())
                        .contains("Disable it instead of deleting it"));

        mockMvc.perform(delete("/api/v1/admin/automation/schedules/{scheduleId}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isConflict())
                .andExpect(result -> assertThat(jsonBody(result).get("detail").asText())
                        .contains("Disable it instead of deleting it"));
    }

    @Test
    void manualAndScheduledTriggersConvergeOnOneLogicalRunPerIdempotencyKey() throws Exception {
        stubAccessibleSource("/converged-feed");
        var bearerToken = bearerToken();

        long topicId = jsonBody(mockMvc.perform(post("/api/v1/admin/automation/topics")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Concurrency Topic",
                                  "promptTemplateVersion":"v1",
                                  "publicationEnabled":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/sources", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType":"HTML",
                                  "sourceUrl":"%s",
                                  "enabled":true
                                }
                                """.formatted(WIREMOCK.baseUrl() + "/converged-feed")))
                .andExpect(status().isCreated());

        var scheduleCreateResult = mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/schedules", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Concurrency schedule",
                                  "cronExpression":"0 15 9 * * *",
                                  "timezone":"Asia/Seoul",
                                  "status":"ACTIVE",
                                  "misfirePolicy":"DO_NOTHING"
                                }
                                """))
                .andReturn();
        assertThat(scheduleCreateResult.getResponse().getStatus()).isEqualTo(201);
        long scheduleId = jsonBody(scheduleCreateResult).get("id").asLong();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<AutomationRunResponse> manual = () -> automationAdminService.triggerManualRun(topicId, "shared-run-key");
            Callable<AutomationRunResponse> scheduled = () -> automationAdminService.triggerScheduledRun(scheduleId, "shared-run-key", Instant.now());
            var futures = executor.invokeAll(List.of(manual, scheduled));
            var responses = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).toList();

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).id()).isEqualTo(responses.get(1).id());
        }

        Integer runCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM automation_run WHERE idempotency_key = ?",
                Integer.class,
                "shared-run-key");
        Integer snapshotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_snapshot",
                Integer.class);

        assertThat(runCount).isEqualTo(1);
        assertThat(snapshotCount).isEqualTo(1);
    }

    @Test
    void administratorCanReadAutomationDiagnosticsSummary() throws Exception {
        stubAccessibleSource("/diagnostics-feed");
        var bearerToken = bearerToken();

        long topicId = jsonBody(mockMvc.perform(post("/api/v1/admin/automation/topics")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Diagnostics Topic",
                                  "promptTemplateVersion":"v1",
                                  "publicationEnabled":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/sources", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType":"HTML",
                                  "sourceUrl":"%s",
                                  "enabled":true
                                }
                                """.formatted(WIREMOCK.baseUrl() + "/diagnostics-feed")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/runs/manual", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"story-10-diagnostics"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/automation/diagnostics")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var json = jsonBody(result);
                    assertThat(json.at("/runCounts/running").asLong()).isGreaterThanOrEqualTo(1L);
                    assertThat(json.at("/jobCounts/pending").asLong()).isGreaterThanOrEqualTo(0L);
                    assertThat(json.at("/outboxCounts/pending").asLong()).isGreaterThanOrEqualTo(0L);
                    assertThat(json.at("/generatedAt").asText()).isNotBlank();
                });
    }

    @Test
    void administratorCanForceRecoveryOfAnExpiredRunUsingDatabaseClock() throws Exception {
        stubAccessibleSource("/recovery-endpoint-feed");
        var bearerToken = bearerToken();

        long topicId = jsonBody(mockMvc.perform(post("/api/v1/admin/automation/topics")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Recovery Endpoint Topic",
                                  "promptTemplateVersion":"v1",
                                  "publicationEnabled":false
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/sources", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType":"HTML",
                                  "sourceUrl":"%s",
                                  "enabled":true
                                }
                                """.formatted(WIREMOCK.baseUrl() + "/recovery-endpoint-feed")))
                .andExpect(status().isCreated());

        long runId = jsonBody(mockMvc.perform(post("/api/v1/admin/automation/topics/{topicId}/runs/manual", topicId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"story-17-recovery-endpoint"}
                                """))
                .andExpect(status().isOk())
                .andReturn()).get("id").asLong();

        mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-recovery-endpoint",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v2"]
                                }
                                """))
                .andExpect(status().isOk());

        jdbcTemplate.update(
                "UPDATE automation_run SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 5 MINUTE WHERE id = ?",
                runId);

        mockMvc.perform(post("/api/v1/admin/automation/runs/{runId}/recovery", runId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var json = jsonBody(result);
                    assertThat(json.get("runId").asLong()).isEqualTo(runId);
                    assertThat(json.get("recovered").asBoolean()).isTrue();
                });

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM automation_run WHERE id = ?",
                String.class,
                runId)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT job_status FROM generation_job WHERE run_id = ?",
                String.class,
                runId)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_entry WHERE target_id = ? AND action_type = 'AUTOMATION_RUN_RECOVERED_AS_FAILED'",
                Integer.class,
                Long.toString(runId))).isEqualTo(1);
    }

    private void stubAccessibleSource(String path) {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withHeader("ETag", "\"feed-v1\"")
                        .withHeader("Last-Modified", "Mon, 29 Jun 2026 12:00:00 GMT")
                        .withBody("""
                                <html>
                                  <head>
                                    <title>Collected source</title>
                                    <link rel="canonical" href="https://example.com%s" />
                                  </head>
                                  <body>
                                    <article><p>Verified source content for automation collection.</p></article>
                                  </body>
                                </html>
                                """.formatted(path))));
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
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(RS256).build(), claims)).getTokenValue();
    }
}
