package com.gtublog.audit;

import java.time.LocalDateTime;

public record AuditEntryResponse(
        Long id,
        AuditActorType actorType,
        String actorId,
        AuditTargetType targetType,
        String targetId,
        String actionType,
        String detailJson,
        LocalDateTime createdAt) {
}
