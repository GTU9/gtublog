package com.gtublog.automation;

import java.time.LocalDateTime;

public record AutomationScheduleResponse(
        Long id,
        Long topicId,
        String name,
        String cronExpression,
        String timezone,
        AutomationScheduleStatus status,
        String misfirePolicy,
        LocalDateTime nextPlannedRunAt,
        AutomationScheduleSyncStatus syncStatus,
        String syncErrorMessage,
        LocalDateTime lastSynchronizedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
