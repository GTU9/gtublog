package com.gtublog.automation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
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
class PublicationOutboxIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";
    private static final String REVALIDATION_SECRET = "story-24-revalidation-secret-32-bytes-minimum";

    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_publication_outbox")
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
        registry.add("app.automation.revalidation.retry-initial-delay", () -> "PT30S");
        registry.add("app.automation.revalidation.retry-max-delay", () -> "PT15M");
        registry.add("app.automation.revalidation.request-timeout", () -> "PT15S");
        registry.add("app.automation.revalidation.lease-duration", () -> "PT30S");
        registry.add("app.automation.revalidation.poll-interval", () -> "PT30S");
        registry.add("app.automation.revalidation.max-attempts", () -> "5");
        registry.add("app.automation.revalidation.batch-size", () -> "20");
        registry.add("app.automation.revalidation.base-url", WIREMOCK::baseUrl);
        registry.add("app.automation.revalidation.shared-secret", () -> REVALIDATION_SECRET);
    }

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PublicationOutboxTransactionService publicationOutboxTransactionService;

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
        WIREMOCK.resetAll();
        jdbcTemplate.update("DELETE FROM post_revision_source_snapshot");
        jdbcTemplate.update("DELETE FROM publication_outbox_event");
        jdbcTemplate.update("DELETE FROM post_tag");
        jdbcTemplate.update("DELETE FROM post_category");
        jdbcTemplate.update("DELETE FROM post_revision");
        jdbcTemplate.update("DELETE FROM source_snapshot");
        jdbcTemplate.update("DELETE FROM post_view_counter");
        jdbcTemplate.update("DELETE FROM post");
        jdbcTemplate.update("DELETE FROM tag");
        jdbcTemplate.update("DELETE FROM category");
        jdbcTemplate.update("DELETE FROM generation_job");
        jdbcTemplate.update("DELETE FROM automation_run");
        jdbcTemplate.update("DELETE FROM automation_schedule");
        jdbcTemplate.update("DELETE FROM automation_source");
        jdbcTemplate.update("DELETE FROM automation_topic");
        jdbcTemplate.update("DELETE FROM audit_entry");
    }

    @Test
    void dueOutboxDeliverySendsSignedVersionOneRevalidationAndSkipsFutureEvents() throws Exception {
        WIREMOCK.stubFor(post(urlEqualTo("/api/revalidate"))
                .willReturn(aResponse().withStatus(204)));
        var dueEventKey = seedOutbox("POST_PUBLISHED", 101L, "due-post", "PENDING", "UTC_TIMESTAMP(6)");
        var futureEventKey = seedOutbox("POST_PUBLISHED", 102L, "future-post", "PENDING", "UTC_TIMESTAMP(6) + INTERVAL 10 MINUTE");

        processOutbox();

        assertThat(outboxStatus(dueEventKey)).as("delivery reason=%s", failureReason(dueEventKey)).isEqualTo("DELIVERED");
        assertThat(outboxStatus(futureEventKey)).isEqualTo("PENDING");
        assertThat(attemptCount(dueEventKey)).isEqualTo(1);
        assertThat(attemptCount(futureEventKey)).isZero();

        var requests = WIREMOCK.findAll(postRequestedFor(urlEqualTo("/api/revalidate")));
        assertThat(requests).hasSize(1);
        var request = requests.getFirst();
        assertThat(request.getHeader("X-Revalidation-Event-Key")).isEqualTo(dueEventKey);
        assertThat(request.getHeader("X-Revalidation-Timestamp")).matches("\\d+");
        assertThat(request.getHeader("X-Revalidation-Signature")).matches("[0-9a-f]{64}");
        assertThat(request.getBody().length).isLessThanOrEqualTo(16 * 1024);
        assertThat(request.getHeader("X-Revalidation-Signature")).isEqualTo(signature(
                request.getHeader("X-Revalidation-Timestamp"),
                dueEventKey,
                request.getBody()));

        var body = objectMapper.readTree(request.getBodyAsString());
        assertThat(body.get("version").asInt()).isEqualTo(1);
        assertThat(body.get("eventKey").asText()).isEqualTo(dueEventKey);
        assertThat(body.get("postId").asLong()).isEqualTo(101L);
        assertThat(body.get("slug").asText()).isEqualTo("due-post");
        assertThat(values(body.get("tags"))).containsExactly("public-posts");
        assertThat(values(body.get("paths"))).contains("/", "/posts/due-post", "/rss.xml", "/sitemap.xml");
    }

    @Test
    void crossProcessFixtureSignatureMatchesVersionOneHmacContract() throws Exception {
        var eventBody = Files.readAllBytes(contractFixture("event.json"));
        var signatureFixture = objectMapper.readTree(Files.readString(contractFixture("signature.json"), StandardCharsets.UTF_8));

        assertThat(eventBody).endsWith((byte) '\n');
        assertThat(signature(
                signatureFixture.get("secret").asText(),
                signatureFixture.get("timestamp").asText(),
                signatureFixture.get("eventKey").asText(),
                eventBody))
                .isEqualTo("8a5882213a0170e7affdc81d8903c3e6c7242f24b6a97a5363711779405e8430")
                .isEqualTo(signatureFixture.get("signature").asText());
    }

    @Test
    void malformedVersionedPayloadIsNotSilentlyRewrittenAsLegacy() throws Exception {
        var wrongKey = seedOutbox("POST_PUBLISHED", 104L, "wrong-key", "PENDING", "UTC_TIMESTAMP(6)");
        var missingVersion = seedOutbox("POST_PUBLISHED", 105L, "missing-version", "PENDING", "UTC_TIMESTAMP(6)");
        jdbcTemplate.update("UPDATE publication_outbox_event SET payload_json = ? WHERE event_key = ?",
                "{\"version\":1,\"eventKey\":\"other-key\",\"slug\":\"wrong-key\"}", wrongKey);
        jdbcTemplate.update("UPDATE publication_outbox_event SET payload_json = ? WHERE event_key = ?",
                "{\"eventKey\":\"" + missingVersion + "\",\"slug\":\"missing-version\"}", missingVersion);

        processOutbox();

        assertThat(WIREMOCK.findAll(postRequestedFor(urlEqualTo("/api/revalidate")))).isEmpty();
        assertThat(outboxStatus(wrongKey)).isEqualTo("PENDING");
        assertThat(outboxStatus(missingVersion)).isEqualTo("PENDING");
        assertThat(failureReason(wrongKey)).isEqualTo("REVALIDATION_PAYLOAD_INVALID");
        assertThat(failureReason(missingVersion)).isEqualTo("REVALIDATION_PAYLOAD_INVALID");
    }

    @Test
    void deliveryClaimsAreCasProtectedAcrossConcurrentWorkers() throws Exception {
        var eventKey = seedOutbox("POST_PUBLISHED", 201L, "race-post", "PENDING", "UTC_TIMESTAMP(6)");
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<java.util.Optional<PublicationOutboxTransactionService.ClaimedOutboxEvent>> claim = () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
            return publicationOutboxTransactionService.claimDueEvent(now, now.plusSeconds(30), 5);
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(claim);
            var second = executor.submit(claim);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(java.util.Optional::isPresent)).hasSize(1);
            var claimed = results.stream().flatMap(java.util.Optional::stream).findFirst().orElseThrow();
            assertThat(claimed.eventKey()).isEqualTo(eventKey);
            assertThat(publicationOutboxTransactionService.completeDelivered(claimed.id(), claimed.claimOwner(), java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))).isTrue();
        }

        assertThat(outboxStatus(eventKey)).isEqualTo("DELIVERED");
        assertThat(attemptCount(eventKey)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT claim_owner IS NULL AND lease_expires_at IS NULL FROM publication_outbox_event WHERE event_key = ?",
                Boolean.class,
                eventKey)).isTrue();
    }

    @Test
    void pendingDueAndExpiredLeasesRetryWhileActiveLeasesAndFutureEventsAreSkipped() throws Exception {
        WIREMOCK.stubFor(post(urlEqualTo("/api/revalidate"))
                .willReturn(aResponse().withStatus(503).withBody("temporarily unavailable")));
        var due = seedOutbox("POST_UPDATED", 301L, "due-retry", "PENDING", "UTC_TIMESTAMP(6)");
        var future = seedOutbox("POST_UPDATED", 302L, "future-retry", "PENDING", "UTC_TIMESTAMP(6) + INTERVAL 10 MINUTE");
        var expired = seedOutbox("POST_UPDATED", 303L, "expired-retry", "IN_FLIGHT", "UTC_TIMESTAMP(6)");
        var active = seedOutbox("POST_UPDATED", 304L, "active-retry", "IN_FLIGHT", "UTC_TIMESTAMP(6)");
        jdbcTemplate.update("UPDATE publication_outbox_event SET attempt_count = 1, claim_owner = ?, lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE event_key = ?", UUID.randomUUID().toString(), expired);
        jdbcTemplate.update("UPDATE publication_outbox_event SET attempt_count = 1, claim_owner = ?, lease_expires_at = UTC_TIMESTAMP(6) + INTERVAL 10 MINUTE WHERE event_key = ?", UUID.randomUUID().toString(), active);

        processOutbox();

        assertThat(WIREMOCK.findAll(postRequestedFor(urlEqualTo("/api/revalidate")))).hasSize(2);
        assertRetryScheduled(due, 1);
        assertRetryScheduled(expired, 2);
        assertThat(outboxStatus(future)).isEqualTo("PENDING");
        assertThat(attemptCount(future)).isZero();
        assertThat(outboxStatus(active)).isEqualTo("IN_FLIGHT");
        assertThat(attemptCount(active)).isEqualTo(1);
    }

    @Test
    void expiredFifthAttemptIsDeadLetteredWithoutAnotherHttpDelivery() throws Exception {
        WIREMOCK.stubFor(post(urlEqualTo("/api/revalidate"))
                .willReturn(aResponse().withStatus(204)));
        var eventKey = seedOutbox("POST_DELETED", 401L, "dead-letter-post", "IN_FLIGHT", "UTC_TIMESTAMP(6)");
        jdbcTemplate.update("UPDATE publication_outbox_event SET attempt_count = 5, claim_owner = ?, lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE event_key = ?", UUID.randomUUID().toString(), eventKey);

        processOutbox();

        assertThat(WIREMOCK.findAll(postRequestedFor(urlEqualTo("/api/revalidate")))).isEmpty();
        assertThat(outboxStatus(eventKey)).isEqualTo("DEAD_LETTER");
        assertThat(attemptCount(eventKey)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT claim_owner IS NULL AND lease_expires_at IS NULL FROM publication_outbox_event WHERE event_key = ?",
                Boolean.class,
                eventKey)).isTrue();
        assertThat(failureReason(eventKey))
                .isNotBlank()
                .doesNotContain(REVALIDATION_SECRET, "X-Revalidation-Signature", "http://", "https://");
    }

    @Test
    void httpDeliveryRunsAfterClaimCommitAndBeforeCompletionTransaction() throws Exception {
        WIREMOCK.stubFor(post(urlEqualTo("/api/revalidate"))
                .willReturn(aResponse().withFixedDelay(1500).withStatus(204)));
        var eventKey = seedOutbox("POST_PUBLISHED", 501L, "transaction-boundary", "PENDING", "UTC_TIMESTAMP(6)");

        try (var executor = Executors.newSingleThreadExecutor()) {
            var delivery = executor.submit(this::processOutboxUnchecked);
            assertThat(observeInFlightBeforeCompletion(eventKey, delivery)).isTrue();
            delivery.get(10, TimeUnit.SECONDS);
        }

        assertThat(outboxStatus(eventKey)).isEqualTo("DELIVERED");
        assertThat(attemptCount(eventKey)).isEqualTo(1);
    }

    @Test
    void manualPublicPostMutationsRecordVersionedRevalidationEventsWithOldAndNewPaths() throws Exception {
        WIREMOCK.stubFor(post(urlEqualTo("/api/revalidate"))
                .willReturn(aResponse().withStatus(503)));
        var bearerToken = bearerToken();
        var categoryId = createCategory(bearerToken, "Story24 Category");
        var tagId = createTag(bearerToken, "Story24 Tag");
        var draft = createPost(bearerToken, "manual-public-old", "Manual public old", categoryId, tagId);

        updatePost(bearerToken, draft.id(), "manual-draft-update", "Draft update", categoryId, tagId);
        assertThat(outboxCount()).isZero();

        publishPost(bearerToken, draft.id());
        var publishedPayload = assertEventPayload("POST_PUBLISHED", draft.id(), "manual-draft-update");
        assertThat(publishedPayload.get("version").asInt()).isEqualTo(1);
        assertThat(values(publishedPayload.get("tags"))).containsExactly("public-posts");
        assertThat(values(publishedPayload.get("paths"))).contains("/posts/manual-draft-update", "/rss.xml", "/sitemap.xml");

        updatePost(bearerToken, draft.id(), "manual-public-new", "Published update", categoryId, tagId);
        var updatedPayload = assertEventPayload("POST_UPDATED", draft.id(), "manual-public-new");
        assertThat(values(updatedPayload.get("paths")))
                .contains("/posts/manual-draft-update", "/posts/manual-public-new", "/", "/rss.xml", "/sitemap.xml");

        restoreRevision(bearerToken, draft.id(), 1);
        var restoredPayload = assertEventPayload("POST_REVISION_RESTORED", draft.id(), "manual-public-new");
        assertThat(values(restoredPayload.get("paths"))).contains("/posts/manual-public-new");

        archivePost(bearerToken, draft.id());
        var archivedPayload = assertEventPayload("POST_ARCHIVED", draft.id(), "manual-public-new");
        assertThat(values(archivedPayload.get("paths"))).contains("/posts/manual-public-new", "/rss.xml", "/sitemap.xml");

        var deleteTarget = createPost(bearerToken, "manual-delete-target", "Manual delete", categoryId, tagId);
        publishPost(bearerToken, deleteTarget.id());
        deletePost(bearerToken, deleteTarget.id());
        var deletedPayload = assertEventPayload("POST_DELETED", deleteTarget.id(), "manual-delete-target");
        assertThat(values(deletedPayload.get("paths"))).contains("/posts/manual-delete-target", "/rss.xml", "/sitemap.xml");

        var countBeforeRestore = outboxCount();
        restoreDeletedPost(bearerToken, deleteTarget.id());
        assertThat(outboxCount()).isEqualTo(countBeforeRestore);
    }

    private boolean observeInFlightBeforeCompletion(String eventKey, java.util.concurrent.Future<?> delivery) throws Exception {
        for (int attempt = 0; attempt < 30 && !delivery.isDone(); attempt++) {
            var row = jdbcTemplate.queryForMap(
                    "SELECT delivery_status, attempt_count, claim_owner, lease_expires_at FROM publication_outbox_event WHERE event_key = ?",
                    eventKey);
            if ("IN_FLIGHT".equals(row.get("delivery_status"))
                    && Integer.valueOf(1).equals(((Number) row.get("attempt_count")).intValue())
                    && row.get("claim_owner") != null
                    && row.get("lease_expires_at") != null) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private void processOutboxUnchecked() {
        try {
            processOutbox();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void processOutbox() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/automation/outbox/process")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk());
    }

    private String seedOutbox(String eventType, Long postId, String slug, String status, String availableAtSql) {
        var eventKey = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO publication_outbox_event
                    (event_key, aggregate_type, aggregate_id, event_type, delivery_status, payload_json, available_at)
                VALUES
                    (?, 'POST', ?, ?, ?, ?, """ + availableAtSql + ")",
                eventKey,
                postId,
                eventType,
                status,
                legacyPayload(postId, slug));
        return eventKey;
    }

    private String legacyPayload(Long postId, String slug) {
        return """
                {"postId":%d,"slug":"%s","paths":["/","/posts/%s","/rss.xml","/sitemap.xml"]}
                """.formatted(postId, slug, slug).trim();
    }

    private void assertRetryScheduled(String eventKey, int expectedAttempts) {
        var row = jdbcTemplate.queryForMap("""
                SELECT delivery_status, attempt_count, claim_owner, lease_expires_at, failure_reason,
                       available_at > UTC_TIMESTAMP(6) AS is_delayed
                  FROM publication_outbox_event
                 WHERE event_key = ?
                """, eventKey);
        assertThat(row.get("delivery_status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempt_count")).intValue()).isEqualTo(expectedAttempts);
        assertThat(row.get("claim_owner")).isNull();
        assertThat(row.get("lease_expires_at")).isNull();
        assertThat(row.get("failure_reason")).asString().isNotBlank().doesNotContain(REVALIDATION_SECRET);
        assertThat(((Number) row.get("is_delayed")).intValue()).isEqualTo(1);
    }

    private JsonNode assertEventPayload(String eventType, long postId, String slug) throws Exception {
        var row = jdbcTemplate.queryForMap("""
                SELECT event_key, payload_json
                  FROM publication_outbox_event
                 WHERE event_type = ? AND aggregate_id = ?
                 ORDER BY id DESC
                 LIMIT 1
                """, eventType, postId);
        var payload = objectMapper.readTree((String) row.get("payload_json"));
        assertThat(payload.get("eventKey").asText()).isEqualTo(row.get("event_key"));
        assertThat(payload.get("postId").asLong()).isEqualTo(postId);
        assertThat(payload.get("slug").asText()).isEqualTo(slug);
        assertThat(payload.get("version").asInt()).isEqualTo(1);
        assertThat(values(payload.get("tags"))).contains("public-posts");
        assertThat(values(payload.get("paths"))).allMatch(path -> path.startsWith("/")).noneMatch(path -> path.startsWith("/admin"));
        return payload;
    }

    private List<String> values(JsonNode array) {
        return array == null || !array.isArray()
                ? List.of()
                : java.util.stream.StreamSupport.stream(array.spliterator(), false)
                        .map(JsonNode::asText)
                        .toList();
    }

    private String outboxStatus(String eventKey) {
        return jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM publication_outbox_event WHERE event_key = ?",
                String.class,
                eventKey);
    }

    private int attemptCount(String eventKey) {
        return jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM publication_outbox_event WHERE event_key = ?",
                Integer.class,
                eventKey);
    }

    private String failureReason(String eventKey) {
        return jdbcTemplate.queryForObject(
                "SELECT failure_reason FROM publication_outbox_event WHERE event_key = ?",
                String.class,
                eventKey);
    }

    private int outboxCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM publication_outbox_event", Integer.class);
    }

    private long createCategory(String bearerToken, String name) throws Exception {
        return jsonBody(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
    }

    private long createTag(String bearerToken, String name) throws Exception {
        return jsonBody(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/taxonomy/tags")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
    }

    private TestPost createPost(String bearerToken, String slug, String title, long categoryId, long tagId) throws Exception {
        var created = jsonBody(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody(slug, title, categoryId, tagId, "Initial revision")))
                .andExpect(status().isCreated())
                .andReturn());
        return new TestPost(created.get("id").asLong(), created.get("slug").asText());
    }

    private void updatePost(String bearerToken, long postId, String slug, String title, long categoryId, long tagId) throws Exception {
        mockMvc.perform(put("/api/v1/admin/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody(slug, title, categoryId, tagId, "Updated revision")))
                .andExpect(status().isOk());
    }

    private String postBody(String slug, String title, long categoryId, long tagId, String note) {
        return """
                {
                  "slug":"%s",
                  "title":"%s",
                  "excerpt":"%s excerpt",
                  "contentMarkdown":"%s markdown",
                  "contentHtml":"<p>%s html</p>",
                  "categoryIds":[%d],
                  "tagIds":[%d],
                  "revisionNote":"%s"
                }
                """.formatted(slug, title, title, title, title, categoryId, tagId, note);
    }

    private void publishPost(String bearerToken, long postId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/posts/{id}/publish", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk());
    }

    private void archivePost(String bearerToken, long postId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/posts/{id}/archive", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk());
    }

    private void deletePost(String bearerToken, long postId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/posts/{id}/delete", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk());
    }

    private void restoreDeletedPost(String bearerToken, long postId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/posts/{id}/restore", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk());
    }

    private void restoreRevision(String bearerToken, long postId, int revisionNumber) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/posts/{id}/revisions/{revisionNumber}/restore", postId, revisionNumber)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk());
    }

    private Path contractFixture(String filename) {
        var rootRelative = Path.of("contracts", "revalidation", "v1", "fixtures", filename);
        if (Files.exists(rootRelative)) {
            return rootRelative;
        }
        return Path.of("..", "contracts", "revalidation", "v1", "fixtures", filename);
    }

    private String signature(String timestamp, String eventKey, byte[] body) throws Exception {
        return signature(REVALIDATION_SECRET, timestamp, eventKey, body);
    }

    private String signature(String secret, String timestamp, String eventKey, byte[] body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '\n');
        mac.update(eventKey.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '\n');
        mac.update(body);
        return HexFormat.of().formatHex(mac.doFinal());
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

    private record TestPost(long id, String slug) {
    }
}
