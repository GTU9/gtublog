package com.gtublog.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("docker")
@SpringBootTest
class DomainFoundationIntegrationTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";

    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_foundation")
            .withUsername("gtublog")
            .withPassword("gtublog-test-password");

    static {
        MYSQL.start();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/mysql")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesInitialMigrationAndExposesExpectedTablesAndConstraints() {
        var tables = jdbcTemplate.queryForList(
                        """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                        """,
                        String.class)
                .stream()
                .collect(Collectors.toSet());

        assertThat(tables)
                .contains(
                        "admin_user",
                        "refresh_token_family",
                        "refresh_token",
                        "post",
                        "post_revision",
                        "category",
                        "tag",
                        "post_category",
                        "post_tag",
                        "source_snapshot",
                        "post_revision_source_snapshot",
                        "automation_topic",
                        "automation_source",
                        "automation_schedule",
                        "automation_run",
                        "generation_job",
                        "publication_outbox_event",
                        "audit_entry",
                        "post_view_counter");

        assertThat(uniqueIndexesFor("admin_user")).contains("uk_admin_user_username");
        assertThat(uniqueIndexesFor("post")).contains("uk_post_slug");
        assertThat(uniqueIndexesFor("automation_run"))
                .contains("uk_automation_run_run_key", "uk_automation_run_idempotency_key");
        assertThat(uniqueIndexesFor("generation_job")).contains("uk_generation_job_job_key");
    }

    private Set<String> uniqueIndexesFor(String tableName) {
        return jdbcTemplate.query(
                        "SHOW INDEX FROM " + tableName,
                        (resultSet, rowNum) ->
                                new IndexRow(resultSet.getString("Key_name"), resultSet.getLong("Non_unique")))
                .stream()
                .filter(IndexRow::isUnique)
                .map(IndexRow::name)
                .collect(Collectors.toSet());
    }

    record IndexRow(String name, long nonUnique) {

        boolean isUnique() {
            return nonUnique == 0;
        }
    }
}
