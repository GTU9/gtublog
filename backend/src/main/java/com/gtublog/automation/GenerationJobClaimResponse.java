package com.gtublog.automation;

import java.time.Instant;
import java.util.List;

public record GenerationJobClaimResponse(
        Long jobId,
        String jobKey,
        Long runId,
        Long topicId,
        String leaseOwner,
        Instant leaseExpiresAt,
        String providerName,
        String promptVersion,
        String schemaVersion,
        String prompt,
        List<SourceSnapshotInput> snapshots) {

    public record SourceSnapshotInput(
            Long snapshotId,
            String sourceUrl,
            String canonicalUrl,
            String title,
            String originHost,
            String bodyExcerpt,
            String contentHash,
            Instant retrievedAt) {
    }
}
