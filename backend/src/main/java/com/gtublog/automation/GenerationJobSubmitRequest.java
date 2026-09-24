package com.gtublog.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.HashSet;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public record GenerationJobSubmitRequest(
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String terminalSubmissionId,
        @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String payloadDigest,
        @NotBlank @Size(max = 120) String workerId,
        @NotBlank @Size(max = 64) String providerName,
        @NotBlank @Size(max = 64) String promptVersion,
        @NotBlank @Pattern(regexp = "^automation-job-v[234]$") String schemaVersion,
        @Valid GeneratedDraft draft,
        @Valid @Size(min = 1, max = 3) List<@Valid @NotNull GeneratedObservation> observations,
        @Valid TaxonomySelection taxonomy,
        @Size(max = 500) String failureReason) {

    public GenerationJobSubmitRequest {
        boolean hasDraft = draft != null;
        boolean hasObservations = observations != null && !observations.isEmpty();
        boolean hasFailure = failureReason != null && !failureReason.isBlank();
        if ((hasDraft ? 1 : 0) + (hasObservations ? 1 : 0) + (hasFailure ? 1 : 0) != 1) {
            throw new IllegalArgumentException("Exactly one terminal success or failure payload is required.");
        }
        if (hasFailure && (taxonomy != null || observations != null || draft != null)) {
            throw new IllegalArgumentException("A failure submission cannot include success payload fields.");
        }
        if (hasDraft && "automation-job-v3".equals(schemaVersion) && draft.taxonomy() == null) {
            throw new IllegalArgumentException("A v3 draft must include taxonomy selection.");
        }
        if (hasDraft && "automation-job-v2".equals(schemaVersion) && draft.taxonomy() != null) {
            throw new IllegalArgumentException("A v2 draft cannot include taxonomy selection.");
        }
        if (hasDraft && "automation-job-v4".equals(schemaVersion)) {
            throw new IllegalArgumentException("A v4 submission cannot include a draft.");
        }
        if (hasObservations && !"automation-job-v4".equals(schemaVersion)) {
            throw new IllegalArgumentException("Structured observations are only accepted for v4 submissions.");
        }
        if (hasObservations && taxonomy == null) {
            throw new IllegalArgumentException("A v4 observation submission must include taxonomy selection.");
        }
        if (!hasObservations && taxonomy != null) {
            throw new IllegalArgumentException("Top-level taxonomy is only accepted with v4 observations.");
        }
    }

    public GenerationJobSubmitRequest(
            String terminalSubmissionId,
            String payloadDigest,
            String workerId,
            String providerName,
            String promptVersion,
            String schemaVersion,
            GeneratedDraft draft,
            String failureReason) {
        this(terminalSubmissionId, payloadDigest, workerId, providerName, promptVersion, schemaVersion,
                draft, null, null, failureReason);
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

    public record GeneratedObservation(
            @NotBlank @Pattern(regexp = "^SOURCE_MENTION$") String kind,
            @NotBlank @Size(max = 1000) String literal,
            @NotNull @Size(min = 2, max = 2)
            List<@NotNull @Positive Long> citationSnapshotIds) {
        public GeneratedObservation {
            if (literal != null) {
                var normalizedLiteral = TerminalPayloadDigester.normalizeEvidenceText(literal);
                if (normalizedLiteral.length() < 20 || normalizedLiteral.length() > 160) {
                    throw new IllegalArgumentException("A v4 observation literal must be 20 to 160 normalized characters.");
                }
                if (containsMarkupOrCategoryC(normalizedLiteral)) {
                    throw new IllegalArgumentException("A v4 observation literal contains unsafe characters.");
                }
            }
            if (citationSnapshotIds != null && new HashSet<>(citationSnapshotIds).size() != citationSnapshotIds.size()) {
                throw new IllegalArgumentException("A v4 observation must cite two distinct snapshots.");
            }
        }

        private static boolean containsMarkupOrCategoryC(String value) {
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                int type = Character.getType(codePoint);
                if (codePoint == '<'
                        || codePoint == '>'
                        || type == Character.CONTROL
                        || type == Character.FORMAT
                        || type == Character.PRIVATE_USE
                        || type == Character.SURROGATE
                        || type == Character.UNASSIGNED) {
                    return true;
                }
                offset += Character.charCount(codePoint);
            }
            return false;
        }
    }

    public record TaxonomySelection(
            @NotNull @Positive @JsonDeserialize(using = StrictTaxonomyIdDeserializer.class) Long categoryId,
            @NotNull @Size(min = 1, max = 5)
            @JsonDeserialize(contentUsing = StrictTaxonomyIdDeserializer.class)
            List<@NotNull @Positive Long> tagIds) {
    }
}
