package com.gtublog.automation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

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
    void claimRequiresWorkerToken() throws Exception {
        mockMvc.perform(post("/api/v1/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v1"]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void workerCanClaimHeartbeatAndSubmitGeneratedDraft() throws Exception {
        var job = seedGenerationJob();

        var claimResult = mockMvc.perform(post("/api/v1/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v1"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        var claimJson = jsonBody(claimResult);
        assertThat(claimJson.get("jobId").asLong()).isEqualTo(job.getId());
        assertThat(claimJson.get("providerName").asText()).isEqualTo("fake-provider");
        assertThat(claimJson.get("snapshots")).hasSize(1);

        mockMvc.perform(post("/api/v1/internal/generation-jobs/{jobId}/heartbeat", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "providerName":"fake-provider",
                                  "promptVersion":"prompt-v1",
                                  "schemaVersion":"automation-job-v1",
                                  "draft":{
                                    "title":"자동 초안",
                                    "excerpt":"요약",
                                    "contentMarkdown":"# 자동 초안\\n\\n본문",
                                    "citationSnapshotIds":[1]
                                  }
                                }
                                """))
                .andExpect(status().isAccepted());

        var storedJob = generationJobRepository.findById(job.getId()).orElseThrow();
        assertThat(storedJob.getJobStatus()).isEqualTo(GenerationJobStatus.SUBMITTED);
        assertThat(storedJob.getResultPayloadJson()).contains("자동 초안");
        assertThat(storedJob.getSubmittedAt()).isNotNull();
    }

    @Test
    void rejectsSubmitFromAnotherWorkerOrExpiredLease() throws Exception {
        var job = seedGenerationJob();

        mockMvc.perform(post("/api/v1/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v1"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-b",
                                  "providerName":"fake-provider",
                                  "promptVersion":"prompt-v1",
                                  "schemaVersion":"automation-job-v1",
                                  "failureReason":"other worker"
                                }
                                """))
                .andExpect(status().isBadRequest());

        jdbcTemplate.update("UPDATE generation_job SET lease_expires_at = '2026-06-28 00:00:00.000000' WHERE id = ?", job.getId());

        mockMvc.perform(post("/api/v1/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, workerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"worker-a",
                                  "providerName":"fake-provider",
                                  "promptVersion":"prompt-v1",
                                  "schemaVersion":"automation-job-v1",
                                  "failureReason":"lease expired"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNoContentWhenNoCompatibleJobExists() throws Exception {
        seedGenerationJob();

        mockMvc.perform(post("/api/v1/internal/generation-jobs/claim")
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

    private GenerationJob seedGenerationJob() {
        stubAccessibleSource("/worker-feed");
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Automation Worker",
                "prompt-v1",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML,
                WIREMOCK.baseUrl() + "/worker-feed",
                true));
        var run = automationAdminService.triggerManualRun(topic.id(), "story-8-run");
        return generationJobRepository.findByRunId(run.id()).orElseThrow();
    }

    private String workerToken() {
        return "worker-test-token";
    }

    private void stubAccessibleSource(String path) {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("""
                                <html>
                                  <head>
                                    <title>Worker source</title>
                                    <link rel="canonical" href="https://example.com%s" />
                                  </head>
                                  <body>
                                    <p>Verified worker content excerpt.</p>
                                  </body>
                                </html>
                                """.formatted(path))));
    }

    private JsonNode jsonBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
