package com.gtublog.automation;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        List<SourceSnapshotInput> snapshots,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaxonomyCatalog taxonomyCatalog) {

    public GenerationJobClaimResponse(Long jobId, String jobKey, Long runId, Long topicId, String leaseOwner,
            Instant leaseExpiresAt, String providerName, String promptVersion, String schemaVersion,
            String prompt, List<SourceSnapshotInput> snapshots) {
        this(jobId, jobKey, runId, topicId, leaseOwner, leaseExpiresAt, providerName, promptVersion,
                schemaVersion, prompt, snapshots, null);
    }

    public record TaxonomyCatalog(List<TaxonomyItem> categories, List<TaxonomyItem> tags) {
    }

    public record TaxonomyItem(Long id, String slug, String name) {
    }

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
