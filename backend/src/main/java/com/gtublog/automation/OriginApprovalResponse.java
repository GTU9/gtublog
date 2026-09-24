package com.gtublog.automation;

import java.time.LocalDateTime;

public record OriginApprovalResponse(Long id, Long sourceId, String originHost, Long groupId,
                                     String groupName, String rationale, String revocationRationale,
                                     boolean active, long revision,
                                     LocalDateTime approvedAt, LocalDateTime revokedAt,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {}
