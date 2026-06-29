package com.gtublog.automation;

import java.time.LocalDateTime;

public record AutomationSourceResponse(
        Long id,
        Long topicId,
        AutomationSourceType sourceType,
        String sourceUrl,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
