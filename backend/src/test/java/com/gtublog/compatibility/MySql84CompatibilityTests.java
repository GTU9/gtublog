package com.gtublog.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("docker")
@Testcontainers
class MySql84CompatibilityTests {

    private static final String MYSQL_IMAGE =
            "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(
                    DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("gtublog_compatibility")
            .withUsername("gtublog")
            .withPassword("gtublog-test-password");

    @Test
    void startsPinnedMySql84AndRunsFlywayMigration() throws Exception {
        var flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/compatibility")
                .load();

        var migrationResult = flyway.migrate();

        assertThat(migrationResult.success).isTrue();
        assertThat(migrationResult.migrationsExecuted).isEqualTo(1);

        try (var connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var versionStatement = connection.createStatement();
                var versionResult = versionStatement.executeQuery("SELECT VERSION()");
                var probeStatement = connection.createStatement();
                var probeResult = probeStatement.executeQuery(
                        "SELECT probe_value FROM phase0_compatibility_probe WHERE id = 1")) {
            assertThat(versionResult.next()).isTrue();
            assertThat(versionResult.getString(1)).startsWith("8.4.");
            assertThat(probeResult.next()).isTrue();
            assertThat(probeResult.getString(1)).isEqualTo("flyway-connected");
        }
    }
}
