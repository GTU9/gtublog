package com.gtublog.automation;

import java.time.LocalDateTime;

public record GenerationJobHeartbeatResponse(Long jobId, String status, LocalDateTime leaseExpiresAt) {
}
