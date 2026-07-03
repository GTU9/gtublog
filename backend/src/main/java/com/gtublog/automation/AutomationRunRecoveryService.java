package com.gtublog.automation;

import com.gtublog.observability.PlatformMetricsService;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AutomationRunRecoveryService {

    private static final int RECOVERY_BATCH_SIZE = 50;

    private final AutomationRunRepository automationRunRepository;
    private final AutomationRunRecoveryTransaction recoveryTransaction;
    private final PlatformMetricsService platformMetricsService;

    public AutomationRunRecoveryService(
            AutomationRunRepository automationRunRepository,
            AutomationRunRecoveryTransaction recoveryTransaction,
            PlatformMetricsService platformMetricsService) {
        this.automationRunRepository = automationRunRepository;
        this.recoveryTransaction = recoveryTransaction;
        this.platformMetricsService = platformMetricsService;
    }

    public int recoverExpiredRuns() {
        var now = automationRunRepository.currentDatabaseUtc();
        var candidateIds = automationRunRepository.findExpiredRunIds(now, PageRequest.of(0, RECOVERY_BATCH_SIZE));
        int recovered = 0;
        for (Long runId : candidateIds) {
            if (recoveryTransaction.recover(runId, now)) {
                platformMetricsService.recordRunRecovery("run_deadline_expired");
                recovered++;
            }
        }
        return recovered;
    }
}
