package com.gtublog.automation;

import java.time.Instant;

public record GenerationJobHeartbeatResponse(Long jobId, String status, Instant serverTime, Instant leaseExpiresAt) {
}
