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

    @Test
    void v17ClaimBackfillUsesDeterministicRepresentativePostAndPreservesExistingContent() throws Exception {
        var migration = Files.readString(Path.of(
                "src/main/resources/db/migration/mysql/V17__automation_publication_claims.sql"));

        assertThat(migration)
                .contains("CREATE TABLE automation_publication_claim")
                .contains("CONSTRAINT uk_automation_publication_claim_type_hash UNIQUE (claim_type, claim_hash)")
                .contains("'SOURCE_FINGERPRINT'")
                .contains("'CANONICAL_URL'")
                .contains("MIN(id)")
                .contains("MIN(post_record.id)")
                .contains("GROUP BY source_fingerprint")
                .contains("GROUP BY SHA2(snapshot.canonical_url, 256), snapshot.canonical_url")
                .doesNotContain("UPDATE post")
                .doesNotContain("DELETE FROM post")
                .doesNotContain("UPDATE source_snapshot")
                .doesNotContain("DELETE FROM source_snapshot");
    }
}
