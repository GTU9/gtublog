package com.gtublog.automation;

import java.time.LocalDateTime;

public record OriginGroupResponse(Long id, Long topicId, String name, String rationale,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {}
