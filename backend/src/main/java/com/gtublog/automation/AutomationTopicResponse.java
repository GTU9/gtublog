package com.gtublog.automation;

import java.time.LocalDateTime;

public record AutomationTopicResponse(
        Long id,
        String slug,
        String name,
        String promptTemplateVersion,
        boolean publicationEnabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
