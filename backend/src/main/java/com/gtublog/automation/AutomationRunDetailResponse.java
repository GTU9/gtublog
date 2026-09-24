package com.gtublog.automation;

import com.gtublog.source.SourcePolicyResult;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

public record AutomationRunDetailResponse(
        AutomationRunResponse run,
        List<SourceSnapshotResponse> snapshots,
        PublicationDecisionResponse publicationDecision,
        GeneratedDraftResponse generatedDraft,
        AvailableActionsResponse availableActions) {

    public record GeneratedDraftResponse(
            String title,
            String excerpt,
            String contentMarkdown,
            List<Long> citationSnapshotIds,
            @JsonInclude(JsonInclude.Include.NON_NULL) GenerationJobSubmitRequest.TaxonomySelection taxonomy) {
    }

    public record AvailableActionsResponse(
            boolean canRetry,
            boolean canCancel,
            boolean canOverridePublish) {
    }

    public record SourceSnapshotResponse(
            Long id,
            Long automationSourceId,
            String sourceUrl,
            String fetchedUrl,
            String canonicalUrl,
            String originHost,
            String title,
            int httpStatus,
            SourcePolicyResult policyResult,
            String contentHash,
            String bodyTextHash,
            String lineageExtractionStatus,
            List<String> explicitUpstreamUrls,
            LocalDateTime retrievedAt) {
    }

    public record PublicationDecisionResponse(
            String outcome,
            String holdReason,
            String detailReason,
            String decisionJson,
            List<SourceRelationDiagnosticResponse> relations) {
    }

    public record SourceRelationDiagnosticResponse(
            Long leftSnapshotId,
            Long rightSnapshotId,
            String relationType,
            String evidenceValue) {
    }
}
