package com.gtublog.automation;

import java.time.LocalDateTime;

public record OriginPairResponse(
        Long id,
        Long topicId,
        Long groupLowId,
        Long groupHighId,
        String groupLowName,
        String groupHighName,
        String rationale,
        String revocationRationale,
        boolean active,
        long revision,
        LocalDateTime approvedAt,
        LocalDateTime revokedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
