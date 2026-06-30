package com.gtublog.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GenerationContractMigrationScriptTests {

    @Test
    void v2MigrationBlocksOnlyActiveV1JobsAndPreservesTerminalHistory() throws Exception {
        var migration = Files.readString(Path.of(
                "src/main/resources/db/migration/mysql/V8__generation_job_contract_v2.sql"));

        assertThat(migration)
                .contains("schema_version = 'automation-job-v1'")
                .contains("job_status IN ('PENDING', 'CLAIMED')")
                .contains("SIGNAL SQLSTATE '45000'")
                .contains("terminal_submission_id")
                .contains("terminal_payload_digest")
                .doesNotContain("UPDATE generation_job")
                .doesNotContain("DELETE FROM generation_job");
    }
}
