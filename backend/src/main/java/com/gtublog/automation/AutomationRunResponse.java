package com.gtublog.automation;

import java.time.LocalDateTime;

public record AutomationRunResponse(
        Long id,
        String runKey,
        Long topicId,
        Long scheduleId,
        String triggerType,
        AutomationRunStatus status,
        String idempotencyKey,
        String holdReason,
        long snapshotCount,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
