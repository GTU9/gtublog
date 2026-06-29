package com.gtublog.automation;

import java.time.LocalDateTime;
import java.util.List;

public record AutomationDiagnosticsResponse(
        RunCounts runCounts,
        JobCounts jobCounts,
        OutboxCounts outboxCounts,
        long heldSnapshotCount,
        List<String> recentHoldReasons,
        LocalDateTime generatedAt) {

    public record RunCounts(
            long running,
            long succeeded,
            long held,
            long failed) {
    }

    public record JobCounts(
            long pending,
            long claimed,
            long submitted,
            long failed) {
    }

    public record OutboxCounts(
            long pending,
            long delivered) {
    }
}
