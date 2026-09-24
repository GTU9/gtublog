package com.gtublog.automation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
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
class SourceCollectionIntegrationTests {

    @TestConfiguration
    static class SourceCollectionTestConfig {

        @Bean
        @Primary
        PinnedSourceHttpClient sourceCollectionPinnedSourceHttpClient() {
            return new PinnedSourceHttpClient(Duration.ofSeconds(5), Duration.ofSeconds(30));
        }
    }

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";

    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_source_collection")
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
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AutomationAdminService automationAdminService;

    @Autowired
    private GenerationJobService generationJobService;

    @Autowired
    private AutomationProperties automationProperties;

    @Autowired
    private AutomationScheduleSynchronizer automationScheduleSynchronizer;

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
    void collectsRssItemArticlesAsSourceSnapshotsInsteadOfTheFeed() {
        stubRssFeed("/rss.xml", """
                <rss version="2.0">
                  <channel>
                    <title>Security Feed</title>
                    <item>
                      <title>First RSS title</title>
                      <link>%s/rss-article-one</link>
                      <description>Feed summary must not replace fetched article text.</description>
                    </item>
                    <item>
                      <title>Second RSS title</title>
                      <link>%s/rss-article-two</link>
                      <description>Second feed summary.</description>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl(), WIREMOCK.baseUrl()));
        stubArticle("/rss-article-one", "https://news.example.com/rss-one", "Fetched RSS Article One", "Article body one proves article fetch.");
        stubArticle("/rss-article-two", "https://news.example.com/rss-two", "Fetched RSS Article Two", "Article body two proves article fetch.");

        var run = triggerFeedRun("/rss.xml", "rss-articles");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("source_url"))
                .containsExactly(
                        WIREMOCK.baseUrl() + "/rss-article-one",
                        WIREMOCK.baseUrl() + "/rss-article-two");
        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("canonical_url"))
                .containsExactly("https://news.example.com/rss-one", "https://news.example.com/rss-two");
        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("title"))
                .containsExactly("Fetched RSS Article One", "Fetched RSS Article Two");
        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("body_excerpt"))
                .allSatisfy(excerpt -> assertThat((String) excerpt).contains("proves article fetch"));
    }

    @Test
    void retainsNormalizedArticleEvidenceBeyondExcerptAndHashesTheStoredText() throws Exception {
        stubRssFeed("/evidence.xml", """
                <rss version="2.0"><channel><item>
                  <title>Evidence entry</title><link>%s/evidence-article</link>
                </item></channel></rss>
                """.formatted(WIREMOCK.baseUrl()));
        var articleText = "Opening " + "x".repeat(1100) + "  Mixed   Case  Café ending";
        stubArticle("/evidence-article", "https://news.example.com/evidence", "Evidence article", articleText);

        var run = triggerFeedRun("/evidence.xml", "article-evidence");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            var evidence = (String) row.get("article_evidence_text");
            assertThat(evidence).isEqualTo("Opening " + "x".repeat(1100) + " Mixed Case Café ending");
            assertThat(evidence).contains("Café ending");
            assertThat((String) row.get("body_excerpt")).hasSize(1000).doesNotContain("Café ending");
            assertThat(row.get("article_evidence_hash")).isEqualTo(sha256(evidence));
            assertThat(row.get("article_evidence_truncated")).isEqualTo(false);
        });

        // A later change at the origin cannot rewrite evidence already committed by Spring.
        stubArticle("/evidence-article", "https://news.example.com/evidence", "Evidence article", "Changed later");
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row ->
                assertThat(row.get("article_evidence_text")).isEqualTo("Opening " + "x".repeat(1100) + " Mixed Case Café ending"));
    }

    @Test
    void truncatesArticleEvidenceWithoutSplittingUtf16SurrogatePair() throws Exception {
        stubRssFeed("/long-evidence.xml", """
                <rss version="2.0"><channel><item>
                  <title>Long evidence entry</title><link>%s/long-evidence-article</link>
                </item></channel></rss>
                """.formatted(WIREMOCK.baseUrl()));
        stubArticle(
                "/long-evidence-article",
                "https://news.example.com/long-evidence",
                "Long evidence article",
                "A".repeat(15_999) + "😀" + "Beyond retained prefix");

        var run = triggerFeedRun("/long-evidence.xml", "long-evidence");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            var evidence = (String) row.get("article_evidence_text");
            assertThat(evidence).isEqualTo("A".repeat(15_999));
            assertThat(row.get("article_evidence_hash")).isEqualTo(sha256(evidence));
            assertThat(row.get("article_evidence_truncated")).isEqualTo(true);
        });
    }

    @Test
    void pageWithoutArticleHasNoVerifiableEvidence() {
        stubRssFeed("/no-article.xml", """
                <rss version="2.0"><channel><item>
                  <title>Page without article</title><link>%s/no-article-page</link>
                </item></channel></rss>
                """.formatted(WIREMOCK.baseUrl()));
        WIREMOCK.stubFor(get(urlEqualTo("/no-article-page"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("<html><body><main>Enough body text to qualify as a fetched page.</main></body></html>")));

        var run = triggerFeedRun("/no-article.xml", "no-article-evidence");

        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("article_evidence_text")).isNull();
            assertThat(row.get("article_evidence_hash")).isNull();
            assertThat(row.get("article_evidence_truncated")).isEqualTo(false);
        });
    }

    @Test
    void partialArticleResponseHasNoVerifiableEvidence() {
        stubRssFeed("/partial-article.xml", """
                <rss version="2.0"><channel><item>
                  <title>Partial article</title><link>%s/partial-article-page</link>
                </item></channel></rss>
                """.formatted(WIREMOCK.baseUrl()));
        WIREMOCK.stubFor(get(urlEqualTo("/partial-article-page"))
                .willReturn(aResponse()
                        .withStatus(206)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("<html><body><article>Only a fragment of the article.</article></body></html>")));

        var run = triggerFeedRun("/partial-article.xml", "partial-article-evidence");

        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("article_evidence_text")).isNull();
            assertThat(row.get("article_evidence_hash")).isNull();
            assertThat(row.get("article_evidence_truncated")).isEqualTo(false);
        });
    }

    @Test
    void collectsRssItemHtml5DoctypeArticleAsAllowedSnapshot() {
        stubRssFeed("/html5-doctype.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>HTML5 article title</title>
                      <link>%s/html5-doctype-article</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        stubHtml5DoctypeArticle(
                "/html5-doctype-article",
                "https://news.example.com/html5-doctype",
                "Fetched HTML5 Doctype Article",
                "HTML5 doctype article body proves normal HTML remains allowed.");

        var run = triggerFeedRun("/html5-doctype.xml", "html5-doctype");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/html5-doctype-article");
            assertThat(row.get("canonical_url")).isEqualTo("https://news.example.com/html5-doctype");
            assertThat(row.get("title")).isEqualTo("Fetched HTML5 Doctype Article");
            assertThat(row.get("policy_result")).isEqualTo("ALLOWED");
            assertThat((String) row.get("body_excerpt")).contains("normal HTML remains allowed");
        });
    }

    @Test
    void collectsRssItemHtml5DoctypeArticleAfterLongLeadingCommentAsAllowedSnapshot() {
        stubRssFeed("/html5-long-comment.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>HTML5 long comment article title</title>
                      <link>%s/html5-long-comment-article</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        stubHtml5DoctypeArticleWithPreamble(
                "/html5-long-comment-article",
                "<!--" + "x".repeat(9000) + "-->\n",
                "https://news.example.com/html5-long-comment",
                "Fetched HTML5 Long Comment Article",
                "HTML5 long comment article body proves normal HTML remains allowed.");

        var run = triggerFeedRun("/html5-long-comment.xml", "html5-long-comment");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/html5-long-comment-article");
            assertThat(row.get("canonical_url")).isEqualTo("https://news.example.com/html5-long-comment");
            assertThat(row.get("title")).isEqualTo("Fetched HTML5 Long Comment Article");
            assertThat(row.get("policy_result")).isEqualTo("ALLOWED");
            assertThat((String) row.get("body_excerpt")).contains("normal HTML remains allowed");
        });
    }

    @Test
    void collectsRssItemHtml401PublicDoctypeArticleAsAllowedSnapshot() {
        stubRssFeed("/html401-public-doctype.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>HTML 4.01 article title</title>
                      <link>%s/html401-public-doctype-article</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        stubHtml401PublicDoctypeArticle(
                "/html401-public-doctype-article",
                "https://news.example.com/html401-public-doctype",
                "Fetched HTML 4.01 Public Doctype Article",
                "HTML 4.01 public doctype article body proves legacy HTML remains allowed.");

        var run = triggerFeedRun("/html401-public-doctype.xml", "html401-public-doctype");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/html401-public-doctype-article");
            assertThat(row.get("canonical_url")).isEqualTo("https://news.example.com/html401-public-doctype");
            assertThat(row.get("title")).isEqualTo("Fetched HTML 4.01 Public Doctype Article");
            assertThat(row.get("policy_result")).isEqualTo("ALLOWED");
            assertThat((String) row.get("body_excerpt")).contains("legacy HTML remains allowed");
        });
    }

    @Test
    void collectsAtomEntryArticlesAsSourceSnapshotsInsteadOfTheFeed() {
        stubAtomFeed("/atom.xml", """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <id>https://feeds.example.com/atom.xml</id>
                  <title>Atom Feed</title>
                  <updated>2026-09-24T08:00:00Z</updated>
                  <entry>
                    <id>https://news.example.com/atom-entry-one</id>
                    <title>Atom entry title</title>
                    <updated>2026-09-24T08:05:00Z</updated>
                    <link href="%s/atom-article-one" />
                    <summary>Atom summary must not replace fetched article text.</summary>
                  </entry>
                </feed>
                """.formatted(WIREMOCK.baseUrl()));
        stubArticle("/atom-article-one", "https://news.example.com/atom-one", "Fetched Atom Article One", "Atom article body proves article fetch.");

        var run = triggerFeedRun("/atom.xml", "atom-articles");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/atom-article-one");
            assertThat(row.get("canonical_url")).isEqualTo("https://news.example.com/atom-one");
            assertThat(row.get("title")).isEqualTo("Fetched Atom Article One");
            assertThat((String) row.get("body_excerpt")).contains("Atom article body proves article fetch.");
        });
    }

    @Test
    void collectsLatinOneRssFeedUsingXmlDeclarationEncoding() {
        stubRssFeedBytes(
                "/latin-one-rss.xml",
                """
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Café RSS title</title>
                      <link>%s/latin-one-article</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()).getBytes(StandardCharsets.ISO_8859_1),
                "application/rss+xml");
        stubArticleWithoutTitle(
                "/latin-one-article",
                "https://news.example.com/latin-one",
                "Latin one article body proves XML declaration decoding.");

        var run = triggerFeedRun("/latin-one-rss.xml", "latin-one-rss");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/latin-one-article");
            assertThat(row.get("canonical_url")).isEqualTo("https://news.example.com/latin-one");
            assertThat(row.get("title")).isEqualTo("Café RSS title");
            assertThat(row.get("policy_result")).isEqualTo("ALLOWED");
        });
    }

    @Test
    void holdsMalformedRssFeedWithoutArticleSnapshots() {
        stubRssFeed("/malformed.xml", """
                <rss version="2.0">
                  <channel>
                    <item><link>%s/never-collected</link>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));

        var run = triggerFeedRun("/malformed.xml", "malformed-feed");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/malformed.xml");
            assertThat(row.get("policy_result")).isEqualTo("HELD");
        });
        assertThat(WIREMOCK.findAll(getRequestedFor(urlEqualTo("/never-collected")))).isEmpty();
    }

    @Test
    void holdsRssFeedThatDeclaresDtdOrExternalEntityWithoutFetchingEntries() {
        stubRssFeed("/external-entity.xml", """
                <!DOCTYPE rss [
                  <!ENTITY secret SYSTEM "file:///etc/passwd">
                ]>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Unsafe entity</title>
                      <link>%s/entity-article</link>
                      <description>&secret;</description>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));

        var run = triggerFeedRun("/external-entity.xml", "external-entity");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/external-entity.xml");
            assertThat(row.get("policy_result")).isEqualTo("HELD");
            assertThat((String) row.get("body_excerpt")).contains("DTDs or entities");
        });
        assertThat(WIREMOCK.findAll(getRequestedFor(urlEqualTo("/entity-article")))).isEmpty();
    }

    @Test
    void holdsEmptyRssFeedWithoutArticleSnapshots() {
        stubRssFeed("/empty.xml", """
                <rss version="2.0">
                  <channel>
                    <title>Empty feed</title>
                  </channel>
                </rss>
                """);

        var run = triggerFeedRun("/empty.xml", "empty-feed");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/empty.xml");
            assertThat(row.get("policy_result")).isEqualTo("HELD");
            assertThat((String) row.get("body_excerpt")).contains("article entries");
        });
    }

    @Test
    void holdsRssFeedWhenAnArticleLinkIsPrivateNetwork() {
        stubRssFeed("/unsafe-link.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Unsafe item</title>
                      <link>http://169.254.169.254/latest/meta-data</link>
                    </item>
                  </channel>
                </rss>
                """);

        var run = triggerFeedRun("/unsafe-link.xml", "unsafe-link");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("policy_result")).isEqualTo("HELD");
            assertThat((String) row.get("body_excerpt")).contains("private network");
        });
    }

    @Test
    void holdsRunWhenAnRssArticleFetchFails() {
        stubRssFeed("/article-failure.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Broken article</title>
                      <link>%s/broken-article</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        WIREMOCK.stubFor(get(urlEqualTo("/broken-article"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("<html><title>Temporarily unavailable</title><body>Retry later.</body></html>")));

        var run = triggerFeedRun("/article-failure.xml", "article-failure");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/broken-article");
            assertThat(row.get("http_status")).isEqualTo(503);
            assertThat(row.get("policy_result")).isEqualTo("HELD");
            assertThat(row.get("article_evidence_text")).isNull();
            assertThat(row.get("article_evidence_hash")).isNull();
            assertThat(row.get("article_evidence_truncated")).isEqualTo(false);
        });
    }

    @Test
    void holdsRssEntryWhenArticleLinkReturnsAnotherXmlFeed() {
        stubRssFeed("/entry-points-to-feed.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Nested feed link</title>
                      <guid isPermaLink="false">nested-feed-entry-guid</guid>
                      <link>%s/not-an-article.xml</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        stubRssFeed("/not-an-article.xml", """
                <rss version="2.0">
                  <channel>
                    <title>Another Feed</title>
                    <item><title>Nested story</title><link>%s/nested-story</link></item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));

        var run = triggerFeedRun("/entry-points-to-feed.xml", "entry-link-returns-feed");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/not-an-article.xml");
            assertThat(row.get("source_feed_url")).isEqualTo(WIREMOCK.baseUrl() + "/entry-points-to-feed.xml");
            assertThat(row.get("source_feed_entry_key")).isEqualTo("nested-feed-entry-guid");
            assertThat(row.get("policy_result")).isEqualTo("HELD");
        });
        assertThat(WIREMOCK.findAll(getRequestedFor(urlEqualTo("/nested-story")))).isEmpty();
    }

    @Test
    void holdsRssEntryWhenArticleLinkReturnsFeedXmlServedAsHtml() {
        stubRssFeed("/entry-points-to-mislabeled-feed.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Mislabeled nested feed link</title>
                      <guid isPermaLink="false">mislabeled-nested-feed-entry-guid</guid>
                      <link>%s/mislabeled-not-an-article</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        WIREMOCK.stubFor(get(urlEqualTo("/mislabeled-not-an-article"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("""
                                <!-- publisher edge comment -->
                                <!-- cache layer comment -->
                                <rss version="2.0">
                                  <channel>
                                    <title>Mislabeled Feed</title>
                                    <item><title>Mislabeled nested story</title><link>%s/mislabeled-nested-story</link></item>
                                  </channel>
                                </rss>
                                """.formatted(WIREMOCK.baseUrl()))));

        var run = triggerFeedRun("/entry-points-to-mislabeled-feed.xml", "entry-link-returns-mislabeled-feed");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/mislabeled-not-an-article");
            assertThat(row.get("source_feed_url")).isEqualTo(WIREMOCK.baseUrl() + "/entry-points-to-mislabeled-feed.xml");
            assertThat(row.get("source_feed_entry_key")).isEqualTo("mislabeled-nested-feed-entry-guid");
            assertThat(row.get("policy_result")).isEqualTo("HELD");
        });
        assertThat(WIREMOCK.findAll(getRequestedFor(urlEqualTo("/mislabeled-nested-story")))).isEmpty();
    }

    @Test
    void holdsRssEntryWhenArticleLinkReturnsFeedXmlAfterLongHtmlCommentPreamble() {
        stubRssFeed("/entry-points-to-long-comment-feed.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Long comment nested feed link</title>
                      <guid isPermaLink="false">long-comment-nested-feed-entry-guid</guid>
                      <link>%s/long-comment-not-an-article</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        var commentPreamble = "<!--" + "x".repeat(9000) + "-->\n"
                + "<!--" + "y".repeat(9000) + "-->\n";
        WIREMOCK.stubFor(get(urlEqualTo("/long-comment-not-an-article"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody(commentPreamble + """
                                <rss version="2.0">
                                  <channel>
                                    <title>Long Comment Mislabeled Feed</title>
                                    <item><title>Long comment nested story</title><link>%s/long-comment-nested-story</link></item>
                                  </channel>
                                </rss>
                                """.formatted(WIREMOCK.baseUrl()))));

        var run = triggerFeedRun("/entry-points-to-long-comment-feed.xml", "entry-link-returns-long-comment-feed");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/long-comment-not-an-article");
            assertThat(row.get("source_feed_url")).isEqualTo(WIREMOCK.baseUrl() + "/entry-points-to-long-comment-feed.xml");
            assertThat(row.get("source_feed_entry_key")).isEqualTo("long-comment-nested-feed-entry-guid");
            assertThat(row.get("policy_result")).isEqualTo("HELD");
        });
        assertThat(WIREMOCK.findAll(getRequestedFor(urlEqualTo("/long-comment-nested-story")))).isEmpty();
    }

    @Test
    void persistsHeldSnapshotWhenRssEntryUrlIsOverlongAndUnsafe() {
        var overlongPrivateUrl = "http://169.254.169.254/latest/meta-data/" + "a".repeat(1500);
        stubRssFeed("/overlong-unsafe.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Overlong unsafe item</title>
                      <link>%s</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(overlongPrivateUrl));

        var run = triggerFeedRun("/overlong-unsafe.xml", "overlong-unsafe");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("policy_result")).isEqualTo("HELD");
            assertThat((String) row.get("source_url")).hasSize(512);
            assertThat((String) row.get("source_url")).startsWith("http://169.254.169.254/latest/meta-data/");
            assertThat((String) row.get("body_excerpt")).contains("Source URL is invalid.");
        });
    }

    @Test
    void preservesRssArticlesThatShareCanonicalUrl() {
        stubRssFeed("/duplicates.xml", """
                <rss version="2.0">
                  <channel>
                    <item><title>Duplicate A</title><link>%s/duplicate-a</link></item>
                    <item><title>Duplicate B</title><link>%s/duplicate-b</link></item>
                    <item><title>Unique</title><link>%s/unique</link></item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl(), WIREMOCK.baseUrl(), WIREMOCK.baseUrl()));
        stubArticle("/duplicate-a", "https://news.example.com/canonical-duplicate", "Duplicate A", "Duplicate article body A.");
        stubArticle("/duplicate-b", "https://news.example.com/canonical-duplicate", "Duplicate B", "Duplicate article body B.");
        stubArticle("/unique", "https://news.example.com/unique", "Unique", "Unique article body.");

        var run = triggerFeedRun("/duplicates.xml", "canonical-dedup");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("canonical_url"))
                .containsExactly(
                        "https://news.example.com/canonical-duplicate",
                        "https://news.example.com/canonical-duplicate",
                        "https://news.example.com/unique");
    }

    @Test
    void preservesRssArticlesThatShareCanonicalUrlAcrossFeedsInTheSameRun() {
        stubRssFeed("/cross-feed-a.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Cross feed A</title>
                      <link>%s/cross-feed-a-article</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        stubRssFeed("/cross-feed-b.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Cross feed B</title>
                      <link>%s/cross-feed-b-article</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        stubArticle(
                "/cross-feed-a-article",
                "https://news.example.com/shared-canonical",
                "Cross Feed A Article",
                "Cross feed article body A.");
        stubArticle(
                "/cross-feed-b-article",
                "https://news.example.com/shared-canonical",
                "Cross Feed B Article",
                "Cross feed article body B.");

        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Source Collection Cross Feed Dedupe",
                "v1",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS,
                WIREMOCK.baseUrl() + "/cross-feed-a.xml",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS,
                WIREMOCK.baseUrl() + "/cross-feed-b.xml",
                true));

        var run = automationAdminService.triggerManualRun(topic.id(), "story-25-cross-feed-canonical-dedup");

        assertThat(snapshotRows(run.id()))
                .filteredOn(row -> "ALLOWED".equals(row.get("policy_result")))
                .extracting(row -> row.get("canonical_url"))
                .containsExactly(
                        "https://news.example.com/shared-canonical",
                        "https://news.example.com/shared-canonical");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT origin_host)
                FROM source_snapshot
                WHERE automation_run_id = ?
                  AND policy_result = 'ALLOWED'
                  AND canonical_url = ?
                """,
                Integer.class,
                run.id(),
                "https://news.example.com/shared-canonical")).isEqualTo(1);
    }

    @Test
    void preservesDuplicateOnlyFeedEvidenceAndContinuesCollecting() {
        stubRssFeed("/duplicate-source-a.xml", """
                <rss version="2.0">
                  <channel>
                    <item><title>Original</title><link>%s/original-article</link></item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        stubRssFeed("/duplicate-source-b.xml", """
                <rss version="2.0">
                  <channel>
                    <item><title>Duplicate only</title><link>%s/duplicate-only-article</link></item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        stubRssFeed("/independent-source-c.xml", """
                <rss version="2.0">
                  <channel>
                    <item><title>Independent</title><link>%s/independent-article</link></item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        stubArticle("/original-article", "https://news.example.com/shared", "Original", "Original article body.");
        stubArticle("/duplicate-only-article", "https://news.example.com/shared", "Duplicate", "Duplicate article body.");
        stubArticle("/independent-article", "https://independent.example.com/story", "Independent", "Independent article body.");

        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Source Collection Duplicate Only Feed",
                "v1",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS,
                WIREMOCK.baseUrl() + "/duplicate-source-a.xml",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS,
                WIREMOCK.baseUrl() + "/duplicate-source-b.xml",
                true));
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS,
                WIREMOCK.baseUrl() + "/independent-source-c.xml",
                true));

        var run = automationAdminService.triggerManualRun(topic.id(), "story-25-duplicate-only-feed-continues");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("canonical_url"))
                .containsExactly(
                        "https://news.example.com/shared",
                        "https://news.example.com/shared",
                        "https://independent.example.com/story");
        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("policy_result"))
                .containsOnly("ALLOWED");
    }

    @Test
    void collectsAtMostTenRssArticleSnapshots() {
        var feedBuilder = new StringBuilder("""
                <rss version="2.0">
                  <channel>
                """);
        for (int index = 1; index <= 12; index++) {
            feedBuilder.append("""
                    <item><title>Bounded %d</title><link>%s/bounded-%d</link></item>
                    """.formatted(index, WIREMOCK.baseUrl(), index));
            stubArticle(
                    "/bounded-" + index,
                    "https://news.example.com/bounded-" + index,
                    "Bounded " + index,
                    "Bounded article body " + index + ".");
        }
        feedBuilder.append("""
                  </channel>
                </rss>
                """);
        stubRssFeed("/bounded.xml", feedBuilder.toString());

        var run = triggerFeedRun("/bounded.xml", "bounded-feed");

        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("source_url"))
                .containsExactly(
                        WIREMOCK.baseUrl() + "/bounded-1",
                        WIREMOCK.baseUrl() + "/bounded-2",
                        WIREMOCK.baseUrl() + "/bounded-3",
                        WIREMOCK.baseUrl() + "/bounded-4",
                        WIREMOCK.baseUrl() + "/bounded-5",
                        WIREMOCK.baseUrl() + "/bounded-6",
                        WIREMOCK.baseUrl() + "/bounded-7",
                        WIREMOCK.baseUrl() + "/bounded-8",
                        WIREMOCK.baseUrl() + "/bounded-9",
                        WIREMOCK.baseUrl() + "/bounded-10");
        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("policy_result"))
                .containsOnly("ALLOWED");
        assertThat(WIREMOCK.findAll(getRequestedFor(urlEqualTo("/bounded-11")))).isEmpty();
        assertThat(WIREMOCK.findAll(getRequestedFor(urlEqualTo("/bounded-12")))).isEmpty();
    }

    @Test
    void holdsRunAndDoesNotCreateWorkerClaimWhenFeedSourcesWouldExceedOneHundredSnapshots() {
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Source Collection More Than One Hundred",
                "v1",
                true));
        for (int feedIndex = 1; feedIndex <= 11; feedIndex++) {
            var feedPath = "/bulk-feed-" + feedIndex + ".xml";
            stubFeedWithTenArticles(feedPath, feedIndex);
            automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                    AutomationSourceType.RSS,
                    WIREMOCK.baseUrl() + feedPath,
                    true));
        }

        var run = automationAdminService.triggerManualRun(topic.id(), "story-25-more-than-one-hundred");

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM generation_job WHERE run_id = ?",
                Integer.class,
                run.id())).isZero();
        assertThat(generationJobService.claim(
                automationProperties.worker().sharedToken(),
                new GenerationJobClaimRequest(
                        "story-25-worker",
                        List.of(automationProperties.worker().preferredProvider()),
                        List.of("automation-job-v2")))).isNull();
    }

    @Test
    void overflowsWhenNinetyNineSnapshotsAreFollowedByPreservedDuplicateOnlyFeedAndOneNewSource() {
        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Source Collection Duplicate Boundary",
                "v1",
                true));
        for (int feedIndex = 1; feedIndex <= 9; feedIndex++) {
            var feedPath = "/boundary-feed-" + feedIndex + ".xml";
            stubFeedWithTenArticles(feedPath, feedIndex);
            automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                    AutomationSourceType.RSS,
                    WIREMOCK.baseUrl() + feedPath,
                    true));
        }
        for (int sourceIndex = 91; sourceIndex <= 99; sourceIndex++) {
            var articlePath = "/boundary-direct-" + sourceIndex;
            stubArticle(
                    articlePath,
                    "https://boundary.example.com/direct-" + sourceIndex,
                    "Boundary Direct " + sourceIndex,
                    "Boundary direct article body " + sourceIndex + ".");
            automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                    AutomationSourceType.HTML,
                    WIREMOCK.baseUrl() + articlePath,
                    true));
        }
        stubRssFeed("/boundary-duplicate-only.xml", """
                <rss version="2.0">
                  <channel>
                    <item><title>Duplicate boundary 1</title><link>%s/boundary-duplicate-1</link></item>
                    <item><title>Duplicate boundary 2</title><link>%s/boundary-duplicate-2</link></item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl(), WIREMOCK.baseUrl()));
        stubArticle("/boundary-duplicate-1", "https://bulk1.example.com/article-1", "Duplicate Boundary 1", "Duplicate body 1.");
        stubArticle("/boundary-duplicate-2", "https://bulk9.example.com/article-10", "Duplicate Boundary 2", "Duplicate body 2.");
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS,
                WIREMOCK.baseUrl() + "/boundary-duplicate-only.xml",
                true));
        stubArticle(
                "/boundary-hundredth",
                "https://boundary.example.com/hundredth",
                "Boundary Hundredth",
                "Boundary hundredth article body.");
        automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.HTML,
                WIREMOCK.baseUrl() + "/boundary-hundredth",
                true));

        var run = automationAdminService.triggerManualRun(topic.id(), "story-25-duplicate-boundary");

        assertThat(run.status())
                .as("run hold: %s; held snapshots: %s", run.holdReason(),
                        snapshotRows(run.id()).stream()
                                .filter(row -> "HELD".equals(row.get("policy_result")))
                                .toList())
                .isEqualTo(AutomationRunStatus.HELD);
        assertThat(snapshotRows(run.id())).hasSize(100);
        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("policy_result"))
                .contains("HELD");
        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("canonical_url"))
                .doesNotContain("https://boundary.example.com/hundredth");
        assertThat(snapshotRows(run.id()))
                .extracting(row -> row.get("body_excerpt"))
                .anySatisfy(excerpt -> assertThat((String) excerpt).contains("exceeded the maximum"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM generation_job WHERE run_id = ?",
                Integer.class,
                run.id())).isZero();
    }

    @Test
    void preservesFeedUrlAndEntryGuidLineageAfterSourceUrlUpdate() {
        stubRssFeed("/lineage-original.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Lineage item</title>
                      <guid isPermaLink="false">entry-guid-001</guid>
                      <link>%s/lineage-article</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(WIREMOCK.baseUrl()));
        stubArticle("/lineage-article", "https://news.example.com/lineage", "Lineage Article", "Lineage article body.");

        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Source Collection Lineage",
                "v1",
                true));
        var source = automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS,
                WIREMOCK.baseUrl() + "/lineage-original.xml",
                true));
        var run = automationAdminService.triggerManualRun(topic.id(), "story-25-lineage");
        automationAdminService.updateSource(source.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS,
                WIREMOCK.baseUrl() + "/lineage-updated.xml",
                true));

        assertThat(run.status()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo(WIREMOCK.baseUrl() + "/lineage-article");
            assertThat(row.get("source_feed_url")).isEqualTo(WIREMOCK.baseUrl() + "/lineage-original.xml");
            assertThat(row.get("source_feed_entry_key")).isEqualTo("entry-guid-001");
        });
    }

    @Test
    void preservesFeedUrlAndEntryGuidLineageWhenRssEntryUrlIsInvalidAfterSourceUrlUpdate() {
        stubRssFeed("/invalid-lineage-original.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Invalid lineage item</title>
                      <guid isPermaLink="false">invalid-entry-guid-001</guid>
                      <link>http://169.254.169.254/latest/meta-data</link>
                    </item>
                  </channel>
                </rss>
                """);

        var topic = automationAdminService.createTopic(new AutomationTopicRequest(
                null,
                "Source Collection Invalid Lineage",
                "v1",
                true));
        var source = automationAdminService.createSource(topic.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS,
                WIREMOCK.baseUrl() + "/invalid-lineage-original.xml",
                true));
        var run = automationAdminService.triggerManualRun(topic.id(), "story-25-invalid-lineage");
        automationAdminService.updateSource(source.id(), new AutomationSourceRequest(
                AutomationSourceType.RSS,
                WIREMOCK.baseUrl() + "/invalid-lineage-updated.xml",
                true));

        assertThat(run.status()).isEqualTo(AutomationRunStatus.HELD);
        assertThat(snapshotRows(run.id())).singleElement().satisfies(row -> {
            assertThat(row.get("source_url")).isEqualTo("http://169.254.169.254/latest/meta-data");
            assertThat(row.get("source_feed_url")).isEqualTo(WIREMOCK.baseUrl() + "/invalid-lineage-original.xml");
            assertThat(row.get("source_feed_entry_key")).isEqualTo("invalid-entry-guid-001");
            assertThat(row.get("policy_result")).isEqualTo("HELD");
        });
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

    private void stubRssFeedBytes(String path, byte[] body, String contentType) {
        WIREMOCK.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", contentType)
                        .withBody(body)));
    }

    private void stubAtomFeed(String path, String body) {
        WIREMOCK.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/atom+xml; charset=utf-8")
                        .withBody(body)));
    }

    private void stubArticle(String path, String canonicalUrl, String title, String bodyText) {
        WIREMOCK.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("""
                                <html>
                                  <head>
                                    <title>%s</title>
                                    <link rel="canonical" href="%s" />
                                  </head>
                                  <body>
                                    <article><p>%s</p></article>
                                  </body>
                                </html>
                        """.formatted(title, canonicalUrl, bodyText))));
    }

    private void stubHtml5DoctypeArticle(String path, String canonicalUrl, String title, String bodyText) {
        stubHtml5DoctypeArticleWithPreamble(path, "", canonicalUrl, title, bodyText);
    }

    private void stubHtml5DoctypeArticleWithPreamble(
            String path,
            String preamble,
            String canonicalUrl,
            String title,
            String bodyText) {
        WIREMOCK.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody(preamble + """
                                <!DOCTYPE html>
                                <html>
                                  <head>
                                    <title>%s</title>
                                    <link rel="canonical" href="%s" />
                                  </head>
                                  <body>
                                    <article><p>%s</p></article>
                                  </body>
                                </html>
                        """.formatted(title, canonicalUrl, bodyText))));
    }

    private void stubHtml401PublicDoctypeArticle(String path, String canonicalUrl, String title, String bodyText) {
        WIREMOCK.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("""
                                <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
                                <html>
                                  <head>
                                    <title>%s</title>
                                    <link rel="canonical" href="%s" />
                                  </head>
                                  <body>
                                    <article><p>%s</p></article>
                                  </body>
                                </html>
                        """.formatted(title, canonicalUrl, bodyText))));
    }

    private void stubArticleWithoutTitle(String path, String canonicalUrl, String bodyText) {
        WIREMOCK.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("""
                                <html>
                                  <head>
                                    <link rel="canonical" href="%s" />
                                  </head>
                                  <body>
                                    <article><p>%s</p></article>
                                  </body>
                                </html>
                        """.formatted(canonicalUrl, bodyText))));
    }

    private void stubFeedWithTenArticles(String feedPath, int feedIndex) {
        var feedBuilder = new StringBuilder("""
                <rss version="2.0">
                  <channel>
                """);
        for (int entryIndex = 1; entryIndex <= 10; entryIndex++) {
            var articlePath = "/bulk-%d-%d".formatted(feedIndex, entryIndex);
            feedBuilder.append("""
                    <item>
                      <title>Bulk %d-%d</title>
                      <link>%s%s</link>
                    </item>
                    """.formatted(feedIndex, entryIndex, WIREMOCK.baseUrl(), articlePath));
            stubArticle(
                    articlePath,
                    "https://bulk%d.example.com/article-%d".formatted(feedIndex, entryIndex),
                    "Bulk " + feedIndex + "-" + entryIndex,
                    "Bulk article body.");
        }
        feedBuilder.append("""
                  </channel>
                </rss>
                """);
        stubRssFeed(feedPath, feedBuilder.toString());
    }

    private List<Map<String, Object>> snapshotRows(Long runId) {
        return jdbcTemplate.queryForList(
                """
                SELECT
                    source_url,
                    source_feed_url,
                    source_feed_entry_key,
                    canonical_url,
                    title,
                    http_status,
                    policy_result,
                    body_excerpt,
                    article_evidence_text,
                    article_evidence_hash,
                    article_evidence_truncated
                FROM source_snapshot
                WHERE automation_run_id = ?
                ORDER BY id ASC
                """,
                runId);
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
