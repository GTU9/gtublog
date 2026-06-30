package com.gtublog.automation;

import com.gtublog.observability.PlatformMetricsService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AutomationRunRecoveryService {

    private static final int RECOVERY_BATCH_SIZE = 50;

    private final AutomationRunRepository automationRunRepository;
    private final AutomationRunRecoveryTransaction recoveryTransaction;
    private final PlatformMetricsService platformMetricsService;
    private final Clock clock;

    public AutomationRunRecoveryService(
            AutomationRunRepository automationRunRepository,
            AutomationRunRecoveryTransaction recoveryTransaction,
            PlatformMetricsService platformMetricsService,
            Clock clock) {
        this.automationRunRepository = automationRunRepository;
        this.recoveryTransaction = recoveryTransaction;
        this.platformMetricsService = platformMetricsService;
        this.clock = clock;
    }

    public int recoverExpiredRuns() {
        var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
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
