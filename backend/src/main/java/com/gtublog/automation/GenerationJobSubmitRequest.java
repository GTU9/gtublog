package com.gtublog.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public record GenerationJobSubmitRequest(
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String terminalSubmissionId,
        @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String payloadDigest,
        @NotBlank @Size(max = 120) String workerId,
        @NotBlank @Size(max = 64) String providerName,
        @NotBlank @Size(max = 64) String promptVersion,
        @NotBlank @Pattern(regexp = "^automation-job-v[23]$") String schemaVersion,
        @Valid GeneratedDraft draft,
        @Size(max = 500) String failureReason) {

    public GenerationJobSubmitRequest {
        boolean hasDraft = draft != null;
        boolean hasFailure = failureReason != null && !failureReason.isBlank();
        if (hasDraft == hasFailure) {
            throw new IllegalArgumentException("Exactly one of draft or failureReason is required.");
        }
        if (hasDraft && "automation-job-v3".equals(schemaVersion) && draft.taxonomy() == null) {
            throw new IllegalArgumentException("A v3 draft must include taxonomy selection.");
        }
        if (hasDraft && "automation-job-v2".equals(schemaVersion) && draft.taxonomy() != null) {
            throw new IllegalArgumentException("A v2 draft cannot include taxonomy selection.");
        }
    }

    public record GeneratedDraft(
            @NotBlank @Size(max = 300) String title,
            @NotBlank @Size(max = 1000) String excerpt,
            @NotBlank @Size(max = 100000) String contentMarkdown,
            @NotNull @Size(min = 1, max = 100)
            List<@Positive Long> citationSnapshotIds,
            @Valid TaxonomySelection taxonomy) {
        public GeneratedDraft(String title, String excerpt, String contentMarkdown, List<Long> citationSnapshotIds) {
            this(title, excerpt, contentMarkdown, citationSnapshotIds, null);
        }
    }

    public record TaxonomySelection(
            @NotNull @Positive @JsonDeserialize(using = StrictTaxonomyIdDeserializer.class) Long categoryId,
            @NotNull @Size(min = 1, max = 5)
            @JsonDeserialize(contentUsing = StrictTaxonomyIdDeserializer.class)
            List<@NotNull @Positive Long> tagIds) {
    }
}
