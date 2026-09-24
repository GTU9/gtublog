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
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
        "app.automation.worker.schema-version=automation-job-v3"
})
class SourceLineagePublicationIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";

    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_source_lineage")
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
    @Autowired private TerminalPayloadDigester terminalPayloadDigester;

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
        jdbcTemplate.update("DELETE FROM automation_publication_claim");
        deleteIfPresent("automation_source_relation_diagnostic");
        deleteIfPresent("automation_publication_decision");
        for (String table : new String[] {
                "post_view_counter", "post_revision_source_snapshot", "publication_outbox_event", "post_revision",
                "post_tag", "post_category", "post", "source_snapshot", "generation_job", "automation_run",
                "automation_schedule", "automation_source", "automation_topic", "tag", "category", "audit_entry"
        }) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @Test
    void preservesBothSnapshotsWhenDifferentPagesShareCanonicalUrl() {
        stubArticle("/same-canonical-a", "https://upstream.example/report-1", """
                <article><p>Original report text with enough detail for hashing and evidence storage.</p></article>
                """);
        stubArticle("/same-canonical-b", "https://upstream.example/report-1", """
                <article><p>Redistributed report text with local wrapper content.</p></article>
                """);

        var job = seedJob("same-canonical", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/same-canonical-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/same-canonical-b")));

        assertThat(job.getId()).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_snapshot WHERE automation_run_id = ? AND canonical_url = ?",
                Integer.class,
                job.getRunId(),
                "https://upstream.example/report-1")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT source_url FROM source_snapshot WHERE automation_run_id = ? ORDER BY id",
                String.class,
                job.getRunId()))
                .containsExactly(
                        WIREMOCK.baseUrl() + "/same-canonical-a",
                        WIREMOCK.baseUrl().replace("localhost", "127.0.0.1") + "/same-canonical-b");
    }

    @Test
    void holdsAutomaticPublicationWhenCitedSnapshotsShareCanonicalUrl() throws Exception {
        stubArticle("/canonical-a", "https://upstream.example/shared-canonical", bodyWithCite(null));
        stubArticle("/canonical-b", "https://upstream.example/shared-canonical", bodyWithCite(null));
        var job = seedJob("shared-canonical", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/canonical-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/canonical-b")));

        var claim = claim("worker-canonical");
        submitDraft(job.getId(), "worker-canonical", claimSnapshotIds(claim), "Shared canonical draft");

        assertHeldWithDetail(job.getRunId(), "SHARED_UPSTREAM");
        assertRelationEvidence(job.getRunId(), "SHARED_UPSTREAM", "https://upstream.example/shared-canonical");
    }

    @Test
    void holdsAutomaticPublicationWhenCitedSnapshotsShareNormalizedText() throws Exception {
        var sharedBody = "Shared normalized body text with enough terms to pass the lineage length threshold. ".repeat(8);
        stubArticle("/normalized-a", "https://site-a.example/report",
                "<h1>Local A</h1><article><p>" + sharedBody + "</p></article>");
        stubArticle("/normalized-b", "https://site-b.example/report",
                "<header>Local B</header><article><div>" + sharedBody + "</div></article>");
        var job = seedJob("shared-normalized-text", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/normalized-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/normalized-b")));

        var claim = claim("worker-normalized");
        submitDraft(job.getId(), "worker-normalized", claimSnapshotIds(claim), "Shared normalized text draft");

        assertHeldWithDetail(job.getRunId(), "SHARED_UPSTREAM");
        var textHash = jdbcTemplate.queryForObject(
                "SELECT body_text_hash FROM source_snapshot WHERE automation_run_id = ? ORDER BY id LIMIT 1",
                String.class,
                job.getRunId());
        assertRelationEvidence(job.getRunId(), "SHARED_UPSTREAM", textHash);
    }

    @Test
    void holdsAutomaticPublicationWhenCitedSnapshotsShareExplicitUpstreamReference() throws Exception {
        var upstream = "https://wire.example/original-report";
        stubArticle("/upstream-a", "https://site-a.example/upstream-a", bodyWithCite(upstream));
        stubArticle("/upstream-b", "https://site-b.example/upstream-b", bodyWithCite(upstream));
        var job = seedJob("shared-upstream", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/upstream-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/upstream-b")));

        var claim = claim("worker-upstream");
        submitDraft(job.getId(), "worker-upstream", claimSnapshotIds(claim), "Shared upstream draft");

        assertHeldWithDetail(job.getRunId(), "SHARED_UPSTREAM");
        assertRelationEvidence(job.getRunId(), "SHARED_UPSTREAM", upstream);
    }

    @Test
    void ordinaryLinksDoNotCreateSharedUpstreamRelationship() throws Exception {
        var ordinaryLink = "https://wire.example/ordinary-link";
        stubArticle("/ordinary-a", "https://site-a.example/ordinary-a", bodyWithOrdinaryLink(ordinaryLink));
        stubArticle("/ordinary-b", "https://site-b.example/ordinary-b", bodyWithOrdinaryLink(ordinaryLink));
        var job = seedJob("ordinary-link", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/ordinary-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/ordinary-b")));

        var claim = claim("worker-ordinary");
        submitDraft(job.getId(), "worker-ordinary", claimSnapshotIds(claim), "Ordinary link draft");

        assertHeldWithDetail(job.getRunId(), "CLAIM_EVIDENCE_UNVERIFIED");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM automation_source_relation_diagnostic
                WHERE run_id = ?
                """,
                Integer.class,
                job.getRunId())).isZero();
    }

    @Test
    void substringRelValuesDoNotCreateSharedUpstreamRelationship() throws Exception {
        var sharedUrl = "https://wire.example/not-cited";
        stubArticle("/recite-a", "https://site-a.example/recite-a", bodyWithRelLink("recite", sharedUrl));
        stubArticle("/excited-b", "https://site-b.example/excited-b", bodyWithRelLink("excited", sharedUrl));
        var job = seedJob("substring-rel", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/recite-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/excited-b")));

        var claim = claim("worker-substring-rel");
        submitDraft(job.getId(), "worker-substring-rel", claimSnapshotIds(claim), "Substring rel draft");

        assertHeldWithDetail(job.getRunId(), "CLAIM_EVIDENCE_UNVERIFIED");
        assertNoRelation(job.getRunId(), "SHARED_UPSTREAM", sharedUrl);
    }

    @Test
    void citeTokenAmongOtherRelTokensCreatesSharedUpstreamRelationship() throws Exception {
        var sharedUrl = "https://wire.example/explicit-cite";
        stubArticle("/rel-cite-a", "https://site-a.example/rel-cite-a", bodyWithRelLink("nofollow cite", sharedUrl));
        stubArticle("/rel-cite-b", "https://site-b.example/rel-cite-b", bodyWithRelLink("cite sponsored", sharedUrl));
        var job = seedJob("rel-cite", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/rel-cite-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/rel-cite-b")));

        var claim = claim("worker-rel-cite");
        submitDraft(job.getId(), "worker-rel-cite", claimSnapshotIds(claim), "Rel cite draft");

        assertHeldWithDetail(job.getRunId(), "SHARED_UPSTREAM");
        assertRelationEvidence(job.getRunId(), "SHARED_UPSTREAM", sharedUrl);
    }

    @Test
    void uncitedSnapshotsCanPersistRelationWithoutIncreasingCitedIndependence() throws Exception {
        stubArticle("/cited-a", "https://site-a.example/cited-a", bodyWithCite(null));
        stubArticle("/cited-b", "https://site-b.example/cited-b", bodyWithCite(null));
        stubArticle("/uncited-c", "https://upstream.example/uncited-shared", bodyWithCite(null));
        stubArticle("/uncited-d", "https://upstream.example/uncited-shared", bodyWithCite(null));
        var job = seedJob("uncited-shared", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/cited-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/cited-b"),
                htmlSource(WIREMOCK.baseUrl(), "/uncited-c"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/uncited-d")));

        var claim = claim("worker-uncited-relation");
        submitDraft(job.getId(), "worker-uncited-relation", claimSnapshotIds(claim, 0, 1), "Uncited relation draft");

        assertHeldWithDetail(job.getRunId(), "CLAIM_EVIDENCE_UNVERIFIED");
        assertRelationEvidence(job.getRunId(), "SHARED_UPSTREAM", "https://upstream.example/uncited-shared");
    }

    @Test
    void citedSnapshotSharingCanonicalWithOmittedSnapshotCreatesSharedUpstreamRelationship() throws Exception {
        stubArticle("/cited-a-shared", "https://upstream.example/partly-cited", bodyWithCite(null));
        stubArticle("/cited-b-unrelated", "https://site-b.example/cited-b-unrelated", bodyWithCite(null));
        stubArticle("/omitted-c-shared", "https://upstream.example/partly-cited", bodyWithCite(null));
        var job = seedJob("omitted-shared", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/cited-a-shared"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/cited-b-unrelated"),
                htmlSource(WIREMOCK.baseUrl(), "/omitted-c-shared")));

        var claim = claim("worker-omitted-shared");
        submitDraft(job.getId(), "worker-omitted-shared", claimSnapshotIds(claim, 0, 1), "Omitted shared draft");

        assertHeldWithDetail(job.getRunId(), "SHARED_UPSTREAM");
        assertRelationEvidence(job.getRunId(), "SHARED_UPSTREAM", "https://upstream.example/partly-cited");
    }

    @Test
    void unrelatedAllowedSourcesStillHoldV3DraftUntilClaimEvidenceExists() throws Exception {
        stubArticle("/unknown-a", "https://site-a.example/unknown-a", bodyWithCite(null));
        stubArticle("/unknown-b", "https://site-b.example/unknown-b", bodyWithCite(null));
        var job = seedJob("unrelated-unknown", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/unknown-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/unknown-b")));

        var claim = claim("worker-unknown");
        submitDraft(job.getId(), "worker-unknown", claimSnapshotIds(claim), "Unknown lineage draft");

        assertHeldWithDetail(job.getRunId(), "CLAIM_EVIDENCE_UNVERIFIED");
        assertThat(automationAdminService.runDetail(job.getRunId()).availableActions().canOverridePublish()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
    }

    @Test
    void sourceBlockedPrecedenceRemainsNonOverrideable() throws Exception {
        stubArticle("/blocked-a", "https://site-a.example/blocked-a", bodyWithCite(null));
        stubArticle("/blocked-b", "https://site-b.example/blocked-b", bodyWithCite(null));
        var job = seedJob("blocked-precedence", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/blocked-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/blocked-b")));
        var claim = claim("worker-blocked");
        var citationIds = claimSnapshotIds(claim);
        jdbcTemplate.update("UPDATE source_snapshot SET policy_result = 'HELD' WHERE id = ?", citationIds.getFirst());

        submitDraft(job.getId(), "worker-blocked", citationIds, "Blocked source draft");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT hold_reason FROM automation_run WHERE id = ?",
                String.class,
                job.getRunId())).isEqualTo(AutomationHoldReason.SOURCE_BLOCKED);
        assertThat(automationAdminService.runDetail(job.getRunId()).availableActions().canOverridePublish()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
    }

    @Test
    void duplicatePublicationPrecedenceRemainsNonOverrideable() throws Exception {
        stubArticle("/duplicate-a", "https://site-a.example/duplicate-a", bodyWithCite(null));
        stubArticle("/duplicate-b", "https://site-b.example/duplicate-b", bodyWithCite(null));
        var firstJob = seedJob("duplicate-first", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/duplicate-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/duplicate-b")));
        var firstClaim = claim("worker-duplicate-first");
        var firstCitationIds = claimSnapshotIds(firstClaim);
        submitDraft(firstJob.getId(), "worker-duplicate-first", firstCitationIds, "Duplicate draft");
        assertHeldWithDetail(firstJob.getRunId(), "CLAIM_EVIDENCE_UNVERIFIED");
        assertThat(automationAdminService.overridePublishHeldRun(firstJob.getRunId()).postId()).isPositive();

        var secondJob = seedJob("duplicate-second", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/duplicate-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/duplicate-b")));
        var secondClaim = claim("worker-duplicate-second");
        submitDraft(secondJob.getId(), "worker-duplicate-second", claimSnapshotIds(secondClaim), "Duplicate draft");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT hold_reason FROM automation_run WHERE id = ?",
                String.class,
                secondJob.getRunId())).isEqualTo(AutomationHoldReason.DUPLICATE_PUBLICATION);
        assertThat(automationAdminService.runDetail(secondJob.getRunId()).availableActions().canOverridePublish()).isFalse();
    }

    @Test
    void sameRunTerminalReplayReturnsStoredHoldWithoutDuplicatingJudgment() throws Exception {
        stubArticle("/replay-a", "https://site-a.example/replay-a", bodyWithCite(null));
        stubArticle("/replay-b", "https://site-b.example/replay-b", bodyWithCite(null));
        var job = seedJob("terminal-replay", List.of(
                htmlSource(WIREMOCK.baseUrl(), "/replay-a"),
                htmlSource(WIREMOCK.baseUrl().replace("localhost", "127.0.0.1"), "/replay-b")));
        var claim = claim("worker-replay");
        var body = draftBody("worker-replay", claimSnapshotIds(claim), "Replay draft");

        var firstResponse = submit(job.getId(), body);
        var retryResponse = submit(job.getId(), body);

        assertThat(jsonBody(retryResponse)).isEqualTo(jsonBody(firstResponse));
        assertHeldWithDetail(job.getRunId(), "CLAIM_EVIDENCE_UNVERIFIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM automation_publication_decision WHERE run_id = ?",
                Integer.class,
                job.getRunId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_entry WHERE action_type = 'GENERATION_JOB_SUBMITTED'",
                Integer.class)).isEqualTo(1);
    }

    private GenerationJob seedJob(String name, List<String> sourceUrls) {
        jdbcTemplate.update("INSERT IGNORE INTO category (slug, name) VALUES (?, ?)", "technology", "Technology");
        jdbcTemplate.update("INSERT IGNORE INTO tag (slug, name) VALUES (?, ?)", "java", "Java");
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Story 28 " + name,
                "prompt-v1",
                true));
        for (String sourceUrl : sourceUrls) {
            automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                    AutomationSourceType.HTML,
                    sourceUrl,
                    true));
        }
        var run = automationAdminService.triggerManualRun(topic.id(), name + "-" + UUID.randomUUID());
        return generationJobRepository.findByRunId(run.id())
                .orElseThrow(() -> new AssertionError("Expected generation job for run " + run.id()
                        + " but run was " + run.status() + " with hold " + run.holdReason()));
    }

    private JsonNode claim(String workerId) throws Exception {
        var result = mockMvc.perform(post("/api/v2/internal/generation-jobs/claim")
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId":"%s",
                                  "supportedProviders":["fake-provider"],
                                  "supportedSchemaVersions":["automation-job-v3"]
                                }
                                """.formatted(workerId)))
                .andExpect(status().isOk())
                .andReturn();
        return jsonBody(result);
    }

    private List<Long> claimSnapshotIds(JsonNode claim) {
        return claimSnapshotIds(claim, 0, 1);
    }

    private List<Long> claimSnapshotIds(JsonNode claim, int firstIndex, int secondIndex) {
        return List.of(
                claim.at("/snapshots/" + firstIndex + "/snapshotId").asLong(),
                claim.at("/snapshots/" + secondIndex + "/snapshotId").asLong());
    }

    private void submitDraft(Long jobId, String workerId, List<Long> citationSnapshotIds, String title) throws Exception {
        submit(jobId, draftBody(workerId, citationSnapshotIds, title));
    }

    private MvcResult submit(Long jobId, String body) throws Exception {
        var result = mockMvc.perform(post("/api/v2/internal/generation-jobs/{jobId}/submit", jobId)
                        .header(GenerationWorkerController.WORKER_TOKEN_HEADER, "worker-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(202);
        return result;
    }

    private String draftBody(String workerId, List<Long> citationSnapshotIds, String title) throws Exception {
        long categoryId = jdbcTemplate.queryForObject("SELECT id FROM category WHERE slug = 'technology'", Long.class);
        long tagId = jdbcTemplate.queryForObject("SELECT id FROM tag WHERE slug = 'java'", Long.class);
        var requestWithoutDigest = new GenerationJobSubmitRequest(
                UUID.randomUUID().toString(),
                "0".repeat(64),
                workerId,
                "fake-provider",
                "prompt-v1",
                "automation-job-v3",
                new GenerationJobSubmitRequest.GeneratedDraft(
                        title,
                        "Evidence summary",
                        "# " + title + "\n\nEvidence-backed body.",
                        citationSnapshotIds,
                        new GenerationJobSubmitRequest.TaxonomySelection(categoryId, List.of(tagId))),
                null);
        var request = new GenerationJobSubmitRequest(
                requestWithoutDigest.terminalSubmissionId(),
                terminalPayloadDigester.digest(requestWithoutDigest),
                requestWithoutDigest.workerId(),
                requestWithoutDigest.providerName(),
                requestWithoutDigest.promptVersion(),
                requestWithoutDigest.schemaVersion(),
                requestWithoutDigest.draft(),
                requestWithoutDigest.failureReason());
        var body = (tools.jackson.databind.node.ObjectNode) objectMapper.valueToTree(request);
        body.remove("failureReason");
        return objectMapper.writeValueAsString(body);
    }

    private void assertHeldWithDetail(Long runId, String detailReason) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM automation_run WHERE id = ?",
                String.class,
                runId)).isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT hold_reason FROM automation_run WHERE id = ?",
                String.class,
                runId)).isEqualTo(AutomationHoldReason.INSUFFICIENT_ORIGINS);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isZero();
        var decision = jdbcTemplate.queryForMap(
                """
                SELECT detail_reason, decision_json
                FROM automation_publication_decision
                WHERE run_id = ?
                """,
                runId);
        assertThat(decision).containsEntry("detail_reason", detailReason);
        assertThat((String) decision.get("decision_json")).contains("\"detailReason\":\"" + detailReason + "\"");
    }

    private void assertRelationEvidence(Long runId, String relationType, String evidenceValue) {
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT evidence_value
                FROM automation_source_relation_diagnostic
                WHERE run_id = ? AND relation_type = ?
                """,
                String.class,
                runId,
                relationType)).contains(evidenceValue);
    }

    private void assertNoRelation(Long runId, String relationType, String evidenceValue) {
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT evidence_value
                FROM automation_source_relation_diagnostic
                WHERE run_id = ? AND relation_type = ?
                """,
                String.class,
                runId,
                relationType)).doesNotContain(evidenceValue);
    }

    private String htmlSource(String baseUrl, String path) {
        return baseUrl + path;
    }

    private String bodyWithCite(String citedUrl) {
        if (citedUrl == null) {
            return """
                    <article><p>Independent local reporting with enough text to be snapshotted for publication review.</p></article>
                    """;
        }
        return """
                <article>
                  <p>Reported summary with explicit attribution to the originating report.</p>
                  <blockquote cite="%s">The originating report establishes the core facts.</blockquote>
                </article>
                """.formatted(citedUrl);
    }

    private String bodyWithOrdinaryLink(String linkedUrl) {
        return """
                <article>
                  <p>Local report body with a normal reading link that is not attribution evidence.</p>
                  <p><a href="%s">Read another article</a></p>
                </article>
                """.formatted(linkedUrl);
    }

    private String bodyWithRelLink(String rel, String linkedUrl) {
        return """
                <article>
                  <p>Local report body with a relationship-marked attribution link.</p>
                  <p><a rel="%s" href="%s">Source report</a></p>
                </article>
                """.formatted(rel, linkedUrl);
    }

    private void stubArticle(String path, String canonicalUrl, String body) {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("""
                                <html>
                                  <head>
                                    <title>Story 28 source</title>
                                    <link rel="canonical" href="%s" />
                                  </head>
                                  <body>%s</body>
                                </html>
                                """.formatted(canonicalUrl, body))));
    }

    private void deleteIfPresent(String tableName) {
        try {
            jdbcTemplate.update("DELETE FROM " + tableName);
        } catch (BadSqlGrammarException ignored) {
        }
    }

    private JsonNode jsonBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
