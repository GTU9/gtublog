package com.gtublog.automation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("docker")
@SpringBootTest(properties = "spring.quartz.auto-startup=false")
class SourceCollectionDeadlineIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";

    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_source_collection_deadline")
            .withUsername("gtublog")
            .withPassword("gtublog-test-password");

    private static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .dynamicPort()
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
        registry.add("app.automation.run.pipeline-lease-duration", () -> "PT30S");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AutomationAdminService automationAdminService;

    @Autowired
    private AutomationScheduleSynchronizer automationScheduleSynchronizer;

    @TestConfiguration
    static class SourceCollectionDeadlineTestConfig {

        @Bean
        @Primary
        PinnedSourceHttpClient sourceCollectionPinnedSourceHttpClient() {
            return new PinnedSourceHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(2));
        }
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
    void holdsRunWithoutWorkerJobWhenAggregateCollectionDeadlineIsAlreadyExhausted() {
        stubRssFeed("/deadline-exhausted.xml", """
                <rss version="2.0">
                  <channel>
                    <item><title>Deadline exhausted</title><link>%s/deadline-exhausted-article</link></item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));

        var run = triggerFeedRun("/deadline-exhausted.xml", "deadline-exhausted");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(run.holdReason()).isEqualTo("1 source snapshots require review.");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT hold_reason FROM automation_run WHERE id = ?",
                String.class,
                run.id())).isEqualTo("1 source snapshots require review.");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM generation_job WHERE run_id = ?",
                Integer.class,
                run.id())).isZero();
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("policy_result")).isEqualTo("HELD");
            assertThat((String) row.get("body_excerpt")).contains("timed out before the run lease");
            assertThat((String) row.get("body_excerpt")).doesNotContain("exceeded the maximum");
        });
        assertThat(WIREMOCK.findAll(getRequestedFor(urlEqualTo("/deadline-exhausted.xml")))).isEmpty();
        assertThat(WIREMOCK.findAll(getRequestedFor(urlEqualTo("/deadline-exhausted-article")))).isEmpty();
    }

    private AutomationRunResponse triggerFeedRun(String feedPath, String idempotencySuffix) {
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Source Collection " + idempotencySuffix,
                "v1",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS,
                WIREMOCK.baseUrl() + feedPath,
                true));
        return automationAdminService.triggerManualRun(topic.id(), "story-25-" + idempotencySuffix);
    }

    private void stubRssFeed(String path, String body) {
        WIREMOCK.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/rss+xml; charset=utf-8")
                        .withBody(body)));
    }

    private List<Map<String, Object>> snapshotRows(Long runId) {
        return jdbcTemplate.queryForList(
                """
                SELECT
                    policy_result,
                    body_excerpt
                FROM source_snapshot
                WHERE automation_run_id = ?
                ORDER BY id ASC
                """,
                runId);
    }
}
