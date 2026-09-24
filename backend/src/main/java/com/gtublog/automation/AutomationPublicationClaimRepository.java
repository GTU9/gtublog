package com.gtublog.automation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AutomationPublicationClaimRepository {

    private static final String FINGERPRINT = "SOURCE_FINGERPRINT";
    private static final String CANONICAL_URL = "CANONICAL_URL";

    private final JdbcTemplate jdbcTemplate;

    AutomationPublicationClaimRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean acquire(Long runId, String fingerprint, List<String> canonicalUrls) {
        if (!insertClaim(FINGERPRINT, fingerprint, fingerprint, runId)) {
            return false;
        }
        for (var canonicalUrl : canonicalUrls.stream().filter(url -> url != null && !url.isBlank()).distinct().toList()) {
            if (!insertClaim(CANONICAL_URL, sha256(canonicalUrl), canonicalUrl, runId)) {
                releaseRunClaims(runId);
                return false;
            }
        }
        return true;
    }

    void attachPost(Long runId, Long postId) {
        jdbcTemplate.update(
                "UPDATE automation_publication_claim SET post_id = ? WHERE automation_run_id = ? AND post_id IS NULL",
                postId,
                runId);
    }

    void releaseRunClaims(Long runId) {
        jdbcTemplate.update(
                "DELETE FROM automation_publication_claim WHERE automation_run_id = ? AND post_id IS NULL",
                runId);
    }

    private boolean insertClaim(String claimType, String claimHash, String claimValue, Long runId) {
        try {
            var inserted = jdbcTemplate.update(
                    """
                    INSERT INTO automation_publication_claim
                        (claim_type, claim_hash, claim_value, automation_run_id, created_at, updated_at)
                    SELECT ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM automation_publication_claim
                        WHERE claim_type = ?
                          AND claim_hash = ?
                    )
                    """,
                    claimType,
                    claimHash,
                    claimValue,
                    runId,
                    claimType,
                    claimHash);
            return inserted == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate automation publication claim hash.", exception);
        }
    }
}
