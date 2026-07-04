package com.gtublog.automation;

import com.gtublog.source.SourcePolicyResult;
import java.time.LocalDateTime;
import java.util.List;

public record AutomationRunDetailResponse(
        AutomationRunResponse run,
        List<SourceSnapshotResponse> snapshots,
        GeneratedDraftResponse generatedDraft,
        AvailableActionsResponse availableActions) {

    public record GeneratedDraftResponse(
            String title,
            String excerpt,
            String contentMarkdown,
            List<Long> citationSnapshotIds) {
    }

    public record AvailableActionsResponse(
            boolean canRetry,
            boolean canCancel,
            boolean canOverridePublish) {
    }

    public record SourceSnapshotResponse(
            Long id,
            String sourceUrl,
            String canonicalUrl,
            String originHost,
            String title,
            int httpStatus,
            SourcePolicyResult policyResult,
            String contentHash,
            LocalDateTime retrievedAt) {
    }
}
