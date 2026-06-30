package com.gtublog.automation;

import java.time.Instant;

public record GenerationJobSubmitResponse(
        Long jobId,
        String status,
        Instant submittedAt,
        String terminalSubmissionId,
        String payloadDigest) {
}
