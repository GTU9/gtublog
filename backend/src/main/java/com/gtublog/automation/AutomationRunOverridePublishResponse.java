package com.gtublog.automation;

public record AutomationRunOverridePublishResponse(
        Long runId,
        Long postId,
        String slug) {
}
