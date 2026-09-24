package com.gtublog.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("docker")
class AutomationPublicationClaimMigrationIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";

    @Test
    void v17BackfillsDeterministicClaimsWithoutMutatingExistingPostsAndRejectsDuplicateClaims() {
        try (var mysql = new MySQLContainer(DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
                .withDatabaseName("gtublog_v17_claim_backfill")
                .withUsername("gtublog")
                .withPassword("gtublog-test-password")) {
            mysql.start();
            migrateTo(mysql, "16");
            var jdbcTemplate = jdbcTemplate(mysql);
            seedExistingDuplicatePublications(jdbcTemplate);

            migrateTo(mysql, null);

            assertThat(jdbcTemplate.queryForList(
                    """
                    SELECT claim_type, claim_hash, claim_value, post_id
                    FROM automation_publication_claim
                    ORDER BY claim_type, claim_value, post_id
                    """))
                    .extracting(row -> row.get("claim_type") + ":" + row.get("claim_value") + ":" + row.get("post_id"))
                    .containsExactly(
                            "CANONICAL_URL:https://example.test/shared-canonical:1",
                            "SOURCE_FINGERPRINT:" + "a".repeat(64) + ":1",
                            "SOURCE_FINGERPRINT:" + "b".repeat(64) + ":3");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class)).isEqualTo(3);
            assertThat(jdbcTemplate.queryForList("SELECT title FROM post ORDER BY id", String.class))
                    .containsExactly("Duplicate fingerprint winner", "Duplicate fingerprint duplicate", "Unique fingerprint");

            assertThatThrownBy(() -> jdbcTemplate.update(
                    """
                    INSERT INTO automation_publication_claim (claim_type, claim_hash, claim_value, post_id)
                    VALUES ('CANONICAL_URL', SHA2('https://example.test/shared-canonical', 256),
                            'https://example.test/shared-canonical', 2)
                    """))
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }

    private void migrateTo(MySQLContainer mysql, String targetVersion) {
        var configuration = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration/mysql");
        if (targetVersion != null) {
            configuration.target(targetVersion);
        }
        configuration.load().migrate();
    }

    private JdbcTemplate jdbcTemplate(MySQLContainer mysql) {
        var dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(mysql.getDriverClassName());
        dataSource.setUrl(mysql.getJdbcUrl());
        dataSource.setUsername(mysql.getUsername());
        dataSource.setPassword(mysql.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private void seedExistingDuplicatePublications(JdbcTemplate jdbcTemplate) {
        insertPost(
                jdbcTemplate,
                1,
                "duplicate-fingerprint-winner",
                "Duplicate fingerprint winner",
                "a".repeat(64));
        insertPost(
                jdbcTemplate,
                2,
                "duplicate-fingerprint-duplicate",
                "Duplicate fingerprint duplicate",
                "a".repeat(64));
        insertPost(
                jdbcTemplate,
                3,
                "unique-fingerprint",
                "Unique fingerprint",
                "b".repeat(64));
        insertRevision(jdbcTemplate, 1, 1);
        insertRevision(jdbcTemplate, 2, 2);
        insertRevision(jdbcTemplate, 3, 3);
        insertSnapshot(jdbcTemplate, 1, "https://example.test/shared-canonical");
        insertSnapshot(jdbcTemplate, 2, "https://example.test/shared-canonical");
        insertSnapshot(jdbcTemplate, 3, "");
        linkCitation(jdbcTemplate, 1, 1);
        linkCitation(jdbcTemplate, 2, 2);
        linkCitation(jdbcTemplate, 3, 3);
    }

    private void insertPost(JdbcTemplate jdbcTemplate, long id, String slug, String title, String fingerprint) {
        jdbcTemplate.update(
                """
                INSERT INTO post
                    (id, slug, title, excerpt, content_markdown, content_html, status, source_fingerprint)
                VALUES (?, ?, ?, 'Existing excerpt', 'Existing markdown', '<p>Existing markdown</p>', 'PUBLISHED', ?)
                """,
                id,
                slug,
                title,
                fingerprint);
    }

    private void insertRevision(JdbcTemplate jdbcTemplate, long id, long postId) {
        jdbcTemplate.update(
                """
                INSERT INTO post_revision
                    (id, post_id, revision_number, title, excerpt, content_markdown, content_html, revision_source)
                VALUES (?, ?, 1, 'Existing title', 'Existing excerpt',
                        'Existing markdown', '<p>Existing markdown</p>', 'AUTOMATION')
                """,
                id,
                postId);
    }

    private void insertSnapshot(JdbcTemplate jdbcTemplate, long id, String canonicalUrl) {
        jdbcTemplate.update(
                """
                INSERT INTO source_snapshot
                    (id, snapshot_key, source_url, canonical_url, origin_host, title, retrieved_at,
                     http_status, content_hash, policy_result)
                VALUES (?, UUID(), CONCAT('https://source.example/article-', ?), NULLIF(?, ''),
                        'source.example', 'Existing source', UTC_TIMESTAMP(6), 200, ?, 'ALLOWED')
                """,
                id,
                id,
                canonicalUrl,
                "c".repeat(64));
    }

    private void linkCitation(JdbcTemplate jdbcTemplate, long revisionId, long snapshotId) {
        jdbcTemplate.update(
                """
                INSERT INTO post_revision_source_snapshot
                    (post_revision_id, source_snapshot_id, citation_order)
                VALUES (?, ?, 1)
                """,
                revisionId,
                snapshotId);
    }
}
