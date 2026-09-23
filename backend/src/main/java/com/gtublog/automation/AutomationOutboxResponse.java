package com.gtublog.automation;

import java.time.LocalDateTime;

public record AutomationOutboxResponse(
        Long id,
        Long aggregateId,
        String deliveryStatus,
        String payloadJson,
        LocalDateTime availableAt,
        LocalDateTime processedAt,
        LocalDateTime lastAttemptAt,
        int attemptCount,
        LocalDateTime leaseExpiresAt,
        String failureReason,
        LocalDateTime createdAt) {
}
