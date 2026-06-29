package com.gtublog.automation;

import java.time.LocalDateTime;

public record GenerationJobSubmitResponse(Long jobId, String status, LocalDateTime submittedAt) {
}
