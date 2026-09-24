package com.gtublog.automation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.core.Options;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
import tools.jackson.databind.node.ObjectNode;

@Tag("docker")
@SpringBootTest(properties = {
        "spring.quartz.auto-startup=false",
        "app.automation.worker.schema-version=automation-job-v3"
})
class GenerationTaxonomyIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";
    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_generation_taxonomy")
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
    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AutomationAdminService automationAdminService;
    @Autowired private AutomationScheduleSynchronizer automationScheduleSynchronizer;
    @Autowired private GenerationJobRepository generationJobRepository;
    @Autowired private TerminalPayloadDigester terminalPayloadDigester;

    private MockMvc mockMvc;

    @Autowired
    void configureMockMvc(WebApplicationContext context) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
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
        jdbcTemplate.update("DELETE FROM post_tag");
        jdbcTemplate.update("DELETE FROM post_category");
        jdbcTemplate.update("DELETE FROM post");
        jdbcTemplate.update("DELETE FROM source_snapshot");
        jdbcTemplate.update("DELETE FROM generation_job");
        jdbcTemplate.update("DELETE FROM automation_run");
        jdbcTemplate.update("DELETE FROM automation_schedule");
        jdbcTemplate.update("DELETE FROM automation_source");
        jdbcTemplate.update("DELETE FROM automation_topic");
        jdbcTemplate.update("DELETE FROM tag");
        jdbcTemplate.update("DELETE FROM category");
        jdbcTemplate.update("DELETE FROM audit_entry");
    }

    @Test
    void v3TypeScriptFixtureDigestMatchesJavaCanonicalDigest() throws Exception {
        var root = Path.of(System.getProperty("user.dir"));
        var contracts = Files.isDirectory(root.resolve("contracts"))
                ? root.resolve("contracts") : root.resolve("..").resolve("contracts").normalize();
        var fixture = Files.readString(contracts.resolve("automation/v3/fixtures/submit-request.json"));
        var request = objectMapper.readValue(fixture, GenerationJobSubmitRequest.class);
        assertThat(terminalPayloadDigester.digest(request)).isEqualTo(request.payloadDigest());
    }

    @Test
    void emptyCatalogHoldsRunBeforeWorkerJobIsCreated() {
        long runId = triggerRun(true);
        assertCatalogHeldWithoutJob(runId, "category or tag");
    }

    @Test
    void oversizedCategoryCatalogHoldsRunBeforeWorkerJobIsCreated() {
        for (int index = 0; index < 101; index++) {
            category("category-" + index, "Category " + index);
        }
        tag("java", "Java");
        assertCatalogHeldWithoutJob(triggerRun(true), "100 categories or 200 tags");
    }

    @Test
    void oversizedTagCatalogHoldsRunBeforeWorkerJobIsCreated() {
        category("technology", "Technology");
        for (int index = 0; index < 201; index++) {
            tag("tag-" + index, "Tag " + index);
        }
        assertCatalogHeldWithoutJob(triggerRun(true), "100 categories or 200 tags");
    }

    private void assertCatalogHeldWithoutJob(long runId, String reasonFragment) {
        assertThat(generationJobRepository.findByRunId(runId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, runId))
                .isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT hold_reason FROM automation_run WHERE id = ?", String.class, runId))
                .containsIgnoringCase(reasonFragment);
        assertThat(count("post")).isZero();
    }

    private long triggerRun(boolean publicationEnabled) {
        stubSource("/taxonomy-source-a", "https://example.com/taxonomy-source-a");
        stubSource("/taxonomy-source-b", "https://example.org/taxonomy-source-b");
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null, "Taxonomy Worker", "prompt-v1", publicationEnabled));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML, WIREMOCK.baseUrl() + "/taxonomy-source-a", true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML,
                WIREMOCK.baseUrl().replace("localhost", "127.0.0.1") + "/taxonomy-source-b", true));
        return automationAdminService.triggerManualRun(topic.id(), "taxonomy-" + UUID.randomUUID()).id();
    }

    @Test
    void v3ClaimPinsOrderedTaxonomyAndOverridePublishesAllLinksAtomically() throws Exception {
        long categoryId = category("technology", "Technology");
        long firstTagId = tag("java", "Java");
        long secondTagId = tag("spring", "Spring");
        var job = seedGenerationJob();
        var storedPayload = objectMapper.readTree(job.getRequestPayloadJson());
        assertThat(storedPayload.at("/taxonomyCatalog/categories/0/id").asLong()).isEqualTo(categoryId);
        assertThat(storedPayload.at("/taxonomyCatalog/tags/0/id").asLong()).isEqualTo(firstTagId);

        var claim = claim("worker-taxonomy");
        assertThat(claim.get("schemaVersion").asText()).isEqualTo("automation-job-v3");
        assertThat(claim.at("/taxonomyCatalog/categories/0/slug").asText()).isEqualTo("technology");
        assertThat(claim.at("/taxonomyCatalog/tags/1/name").asText()).isEqualTo("Spring");
        var result = submit(job.getId(), draftBody("worker-taxonomy", claim, categoryId, List.of(firstTagId, secondTagId)));
        assertThat(result.get("status").asText()).isEqualTo("SUBMITTED");

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT hold_reason FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .isEqualTo(AutomationHoldReason.INSUFFICIENT_ORIGINS);
        assertThat(count("post")).isZero();
        assertThat(count("post_revision")).isZero();
        assertThat(count("post_revision_source_snapshot")).isZero();
        assertThat(count("post_category")).isZero();
        assertThat(count("post_tag")).isZero();
        assertThat(count("publication_outbox_event")).isZero();

        assertThat(automationAdminService.overridePublishHeldRun(job.getRunId()).postId()).isPositive();
        assertThat(count("post")).isEqualTo(1);
        assertThat(count("post_revision")).isEqualTo(1);
        assertThat(count("post_revision_source_snapshot")).isEqualTo(2);
        assertThat(count("post_category")).isEqualTo(1);
        assertThat(count("post_tag")).isEqualTo(2);
        assertThat(count("publication_outbox_event")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT category_id FROM post_category", Long.class)).containsExactly(categoryId);
        assertThat(jdbcTemplate.queryForList("SELECT tag_id FROM post_tag ORDER BY tag_id", Long.class))
                .containsExactly(firstTagId, secondTagId);
        mockMvc.perform(get("/api/v1/public/categories/{slug}", "technology"))
                .andExpect(status().isOk())
                .andExpect(response -> assertThat(objectMapper.readTree(response.getResponse().getContentAsString())
                        .at("/items/0/title").asText()).isEqualTo("Taxonomy generated post"));
        mockMvc.perform(get("/api/v1/public/tags/{slug}", "java"))
                .andExpect(status().isOk())
                .andExpect(response -> assertThat(objectMapper.readTree(response.getResponse().getContentAsString())
                        .at("/items/0/title").asText()).isEqualTo("Taxonomy generated post"));
        mockMvc.perform(get("/api/v1/public/search").queryParam("q", "technology"))
                .andExpect(status().isOk())
                .andExpect(response -> assertThat(objectMapper.readTree(response.getResponse().getContentAsString())
                        .at("/items/0/title").asText()).isEqualTo("Taxonomy generated post"));
    }

    @Test
    void policyInvalidSelectionIsHeldWithStoredDraftAndNoPublication() throws Exception {
        long categoryId = category("technology", "Technology");
        long tagId = tag("java", "Java");
        var job = seedGenerationJob();
        var claim = claim("worker-invalid-selection");
        submit(job.getId(), draftBody("worker-invalid-selection", claim, categoryId, List.of(tagId, tagId)));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT hold_reason FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .containsIgnoringCase("same tag");
        assertThat(objectMapper.readTree(generationJobRepository.findById(job.getId()).orElseThrow().getResultPayloadJson())
                .at("/taxonomy/tagIds/1").asLong()).isEqualTo(tagId);
        assertThat(count("post")).isZero();
        assertThat(count("post_category")).isZero();
        assertThat(count("post_tag")).isZero();
        assertThat(count("publication_outbox_event")).isZero();
    }

    @Test
    void selectionOutsidePinnedCatalogIsHeldEvenIfTaxonomyWasCreatedLater() throws Exception {
        long categoryId = category("technology", "Technology");
        tag("java", "Java");
        var job = seedGenerationJob();
        var claim = claim("worker-outside-catalog");
        long lateTagId = tag("late", "Late Tag");
        submit(job.getId(), draftBody("worker-outside-catalog", claim, categoryId, List.of(lateTagId)));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT hold_reason FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .containsIgnoringCase("outside");
        assertThat(count("post")).isZero();
        assertThat(count("post_tag")).isZero();
        assertThat(count("publication_outbox_event")).isZero();
    }

    @Test
    void validClassifiedHeldDraftCanBePublishedByAdminOverride() throws Exception {
        long categoryId = category("technology", "Technology");
        long tagId = tag("java", "Java");
        var job = seedGenerationJob(false);
        var claim = claim("worker-override-valid");
        submit(job.getId(), draftBody("worker-override-valid", claim, categoryId, List.of(tagId)));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .isEqualTo("HELD");

        var result = automationAdminService.overridePublishHeldRun(job.getRunId());
        assertThat(result.postId()).isPositive();
        assertThat(count("post")).isEqualTo(1);
        assertThat(count("post_category")).isEqualTo(1);
        assertThat(count("post_tag")).isEqualTo(1);
        assertThat(count("publication_outbox_event")).isEqualTo(1);
    }

    @Test
    void policyInvalidHeldDraftCannotBePublishedByAdminOverride() throws Exception {
        long categoryId = category("technology", "Technology");
        long tagId = tag("java", "Java");
        var job = seedGenerationJob(false);
        var claim = claim("worker-override-invalid");
        submit(job.getId(), draftBody("worker-override-invalid", claim, categoryId, List.of(tagId, tagId)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> automationAdminService.overridePublishHeldRun(job.getRunId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("post")).isZero();
        assertThat(count("post_category")).isZero();
        assertThat(count("post_tag")).isZero();
        assertThat(count("publication_outbox_event")).isZero();
    }

    @Test
    void failureAfterTaxonomyLinksRollsBackEntireOverridePublication() throws Exception {
        long categoryId = category("technology", "Technology");
        long tagId = tag("java", "Java");
        var job = seedGenerationJob();
        var claim = claim("worker-outbox-rollback");
        submit(job.getId(), draftBody("worker-outbox-rollback", claim, categoryId, List.of(tagId)));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .isEqualTo("HELD");
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("SET GLOBAL log_bin_trust_function_creators = 1");
        }
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_taxonomy_outbox BEFORE INSERT ON publication_outbox_event
                FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced outbox failure'
                """);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> automationAdminService.overridePublishHeldRun(job.getRunId()))
                    .isInstanceOf(RuntimeException.class);
            assertThat(generationJobRepository.findById(job.getId()).orElseThrow().getJobStatus())
                    .isEqualTo(GenerationJobStatus.SUBMITTED);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                    .isEqualTo("HELD");
            assertThat(count("post")).isZero();
            assertThat(count("post_revision")).isZero();
            assertThat(count("post_category")).isZero();
            assertThat(count("post_tag")).isZero();
            assertThat(count("publication_outbox_event")).isZero();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_taxonomy_outbox");
        }
    }

    @Test
    void changedCategoryAfterEnqueueIsHeldAndTerminalRetryCannotChangeTaxonomy() throws Exception {
        long categoryId = category("technology", "Technology");
        long tagId = tag("java", "Java");
        long otherTagId = tag("spring", "Spring");
        var job = seedGenerationJob();
        var claim = claim("worker-stale-taxonomy");
        jdbcTemplate.update("UPDATE category SET name = ? WHERE id = ?", "Renamed", categoryId);

        var original = draftBody("worker-stale-taxonomy", claim, categoryId, List.of(tagId));
        var first = submit(job.getId(), original);
        var retry = submit(job.getId(), original);
        assertThat(retry).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .isEqualTo("HELD");
        assertThat(count("post")).isZero();

        var changed = (ObjectNode) objectMapper.readTree(original);
        changed.withObject("/draft/taxonomy").withArray("tagIds").removeAll().add(otherTagId);
        changed.put("payloadDigest", terminalPayloadDigester.digest(
                objectMapper.treeToValue(changed, GenerationJobSubmitRequest.class)));
        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changed)))
                .andExpect(status().isConflict());
        assertThat(count("post")).isZero();
        assertThat(count("publication_outbox_event")).isZero();
    }

    @Test
    void concurrentCategoryRenameWaitsForAdminLockThenHoldsWithoutOrphans() throws Exception {
        long categoryId = category("technology", "Technology");
        long tagId = tag("java", "Java");
        var job = seedGenerationJob();
        var claim = claim("worker-category-race");
        var body = draftBody("worker-category-race", claim, categoryId, List.of(tagId));
        var started = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor(); var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM category WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, categoryId);
                try (var rows = lock.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
            }
            var submission = executor.submit(() -> {
                started.countDown();
                return submit(job.getId(), body);
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(waitForDatabaseRowLock()).isTrue();
            assertThat(submission.isDone()).isFalse();
            try (var rename = connection.prepareStatement("UPDATE category SET name = ? WHERE id = ?")) {
                rename.setString(1, "Renamed by administrator");
                rename.setLong(2, categoryId);
                assertThat(rename.executeUpdate()).isEqualTo(1);
            }
            connection.commit();
            assertThat(submission.get(10, TimeUnit.SECONDS).get("status").asText()).isEqualTo("SUBMITTED");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .isEqualTo("HELD");
        assertThat(count("post")).isZero();
        assertThat(count("post_category")).isZero();
        assertThat(count("post_tag")).isZero();
        assertThat(count("publication_outbox_event")).isZero();
    }

    @Test
    void concurrentTagDeleteWaitsForAdminLockThenHoldsWithoutOrphans() throws Exception {
        long categoryId = category("technology", "Technology");
        long tagId = tag("java", "Java");
        var job = seedGenerationJob();
        var claim = claim("worker-tag-race");
        var body = draftBody("worker-tag-race", claim, categoryId, List.of(tagId));
        var started = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor(); var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM tag WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, tagId);
                try (var rows = lock.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
            }
            var submission = executor.submit(() -> {
                started.countDown();
                return submit(job.getId(), body);
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(waitForDatabaseRowLock()).isTrue();
            assertThat(submission.isDone()).isFalse();
            try (var delete = connection.prepareStatement("DELETE FROM tag WHERE id = ?")) {
                delete.setLong(1, tagId);
                assertThat(delete.executeUpdate()).isEqualTo(1);
            }
            connection.commit();
            assertThat(submission.get(10, TimeUnit.SECONDS).get("status").asText()).isEqualTo("SUBMITTED");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM automation_run WHERE id = ?", String.class, job.getRunId()))
                .isEqualTo("HELD");
        assertThat(count("post")).isZero();
        assertThat(count("post_category")).isZero();
        assertThat(count("post_tag")).isZero();
        assertThat(count("publication_outbox_event")).isZero();
    }

    @Test
    void malformedSelectionReturnsBadRequestWithoutChangingClaimedJob() throws Exception {
        long categoryId = category("technology", "Technology");
        tag("java", "Java");
        var job = seedGenerationJob();
        var claim = claim("worker-bad-json");
        var body = (ObjectNode) objectMapper.readTree(draftBody("worker-bad-json", claim, categoryId, List.of(1L)));
        body.withObject("/draft/taxonomy").put("categoryId", "invalid");
        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertThat(generationJobRepository.findById(job.getId()).orElseThrow().getJobStatus())
                .isEqualTo(GenerationJobStatus.CLAIMED);
        assertThat(count("post")).isZero();
    }

    @Test
    void numericStringTaxonomyIdsAreRejectedWithoutChangingClaimedJob() throws Exception {
        long categoryId = category("technology", "Technology");
        long tagId = tag("java", "Java");
        var job = seedGenerationJob();
        var claim = claim("worker-string-id");
        var body = (ObjectNode) objectMapper.readTree(draftBody("worker-string-id", claim, categoryId, List.of(tagId)));
        body.withObject("/draft/taxonomy").put("categoryId", Long.toString(categoryId));
        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        body.withObject("/draft/taxonomy").put("categoryId", categoryId);
        body.withObject("/draft/taxonomy").withArray("tagIds").removeAll().add(Long.toString(tagId));
        mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertThat(generationJobRepository.findById(job.getId()).orElseThrow().getJobStatus())
                .isEqualTo(GenerationJobStatus.CLAIMED);
        assertThat(count("post")).isZero();
    }

    @Test
    void unknownFieldsAtEveryV3SubmitLevelAreRejectedWithoutMutation() throws Exception {
        long categoryId = category("technology", "Technology");
        long tagId = tag("java", "Java");
        var job = seedGenerationJob();
        var claim = claim("worker-extra-fields");
        var valid = draftBody("worker-extra-fields", claim, categoryId, List.of(tagId));
        for (var level : List.of("root", "draft", "taxonomy")) {
            var body = (ObjectNode) objectMapper.readTree(valid);
            switch (level) {
                case "root" -> body.put("unknownField", true);
                case "draft" -> body.withObject("/draft").put("unknownField", true);
                case "taxonomy" -> body.withObject("/draft/taxonomy").put("unknownField", true);
                default -> throw new IllegalStateException(level);
            }
            mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", job.getId())
                            .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(generationJobRepository.findById(job.getId()).orElseThrow().getJobStatus())
                .isEqualTo(GenerationJobStatus.CLAIMED);
        assertThat(count("post")).isZero();
        assertThat(count("publication_outbox_event")).isZero();
    }

    private GenerationJob seedGenerationJob() {
        return seedGenerationJob(true);
    }

    private GenerationJob seedGenerationJob(boolean publicationEnabled) {
        long runId = triggerRun(publicationEnabled);
        return generationJobRepository.findByRunId(runId).orElseThrow(() -> new IllegalStateException(
                "No v3 job for run " + runId + " (status=" + jdbcTemplate.queryForObject(
                        "SELECT status FROM automation_run WHERE id = ?", String.class, runId)
                        + ", hold=" + jdbcTemplate.queryForObject(
                        "SELECT hold_reason FROM automation_run WHERE id = ?", String.class, runId)
                        + ", snapshots=" + jdbcTemplate.queryForList(
                                "SELECT http_status, policy_result, body_excerpt FROM source_snapshot WHERE automation_run_id = ?",
                                runId) + ")"));
    }

    private void stubSource(String path, String canonicalUrl) {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("<html><head><title>Worker source</title><link rel=\"canonical\" href=\""
                                + canonicalUrl + "\" /></head><body><p>Verified worker content excerpt.</p></body></html>")));
    }

    private long category(String slug, String name) {
        jdbcTemplate.update("INSERT INTO category (slug, name) VALUES (?, ?)", slug, name);
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE slug = ?", Long.class, slug);
    }

    private long tag(String slug, String name) {
        jdbcTemplate.update("INSERT INTO tag (slug, name) VALUES (?, ?)", slug, name);
        return jdbcTemplate.queryForObject("SELECT id FROM tag WHERE slug = ?", Long.class, slug);
    }

    private JsonNode claim(String workerId) throws Exception {
        var result = mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workerId":"%s","supportedProviders":["fake-provider"],
                                 "supportedSchemaVersions":["automation-job-v2","automation-job-v3"]}
                                """.formatted(workerId)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String draftBody(String workerId, JsonNode claim, long categoryId, List<Long> tagIds) throws Exception {
        var body = objectMapper.createObjectNode();
        body.put("terminalSubmissionId", UUID.randomUUID().toString());
        body.put("payloadDigest", "0".repeat(64));
        body.put("workerId", workerId);
        body.put("providerName", "fake-provider");
        body.put("promptVersion", "prompt-v1");
        body.put("schemaVersion", "automation-job-v3");
        var draft = body.putObject("draft");
        draft.put("title", "Taxonomy generated post");
        draft.put("excerpt", "Verified summary");
        draft.put("contentMarkdown", "Verified content from two independent sources.");
        var citations = draft.putArray("citationSnapshotIds");
        citations.add(claim.at("/snapshots/0/snapshotId").asLong());
        citations.add(claim.at("/snapshots/1/snapshotId").asLong());
        var taxonomy = draft.putObject("taxonomy");
        taxonomy.put("categoryId", categoryId);
        var tags = taxonomy.putArray("tagIds");
        tagIds.forEach(tags::add);
        body.put("payloadDigest", terminalPayloadDigester.digest(
                objectMapper.treeToValue(body, GenerationJobSubmitRequest.class)));
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode submit(long jobId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", jobId)
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private boolean waitForDatabaseRowLock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                var statement = connection.createStatement()) {
            while (System.nanoTime() < deadline) {
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM performance_schema.data_lock_waits")) {
                    rows.next();
                    if (rows.getInt(1) > 0) {
                        return true;
                    }
                }
                TimeUnit.MILLISECONDS.sleep(25);
            }
        }
        return false;
    }
}
