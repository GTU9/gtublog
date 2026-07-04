package com.gtublog.automation;

import java.time.LocalDateTime;

public record AutomationRunResponse(
        Long id,
        String runKey,
        Long topicId,
        Long scheduleId,
        Long retryOfRunId,
        String triggerType,
        AutomationRunStatus status,
        String idempotencyKey,
        String holdReason,
        AutomationRunResolutionStatus resolutionStatus,
        String resolutionNote,
        Long resolvedPostId,
        long snapshotCount,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
