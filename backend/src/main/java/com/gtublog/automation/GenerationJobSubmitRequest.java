package com.gtublog.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record GenerationJobSubmitRequest(
        @NotBlank String workerId,
        @NotBlank String providerName,
        @NotBlank String promptVersion,
        @NotBlank String schemaVersion,
        @Valid GeneratedDraft draft,
        String failureReason) {

    public record GeneratedDraft(
            @NotBlank String title,
            @NotBlank String excerpt,
            @NotBlank String contentMarkdown,
            List<Long> citationSnapshotIds) {
    }
}
