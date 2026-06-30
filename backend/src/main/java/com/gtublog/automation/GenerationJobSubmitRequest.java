package com.gtublog.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record GenerationJobSubmitRequest(
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String terminalSubmissionId,
        @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String payloadDigest,
        @NotBlank @Size(max = 120) String workerId,
        @NotBlank @Size(max = 64) String providerName,
        @NotBlank @Size(max = 64) String promptVersion,
        @NotBlank @Pattern(regexp = "^automation-job-v2$") String schemaVersion,
        @Valid GeneratedDraft draft,
        @Size(max = 500) String failureReason) {

    public GenerationJobSubmitRequest {
        boolean hasDraft = draft != null;
        boolean hasFailure = failureReason != null && !failureReason.isBlank();
        if (hasDraft == hasFailure) {
            throw new IllegalArgumentException("Exactly one of draft or failureReason is required.");
        }
    }

    public record GeneratedDraft(
            @NotBlank @Size(max = 300) String title,
            @NotBlank @Size(max = 1000) String excerpt,
            @NotBlank @Size(max = 100000) String contentMarkdown,
            @NotNull @Size(min = 1, max = 100)
            List<@Positive Long> citationSnapshotIds) {
    }
}
