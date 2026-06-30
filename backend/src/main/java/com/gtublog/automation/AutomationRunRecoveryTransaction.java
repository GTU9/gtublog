package com.gtublog.automation;

import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationRunRecoveryTransaction {

    static final String EXPIRED_REASON = "Automation run deadline expired before a terminal result was committed.";

    private final AutomationRunRepository automationRunRepository;
    private final GenerationJobRepository generationJobRepository;
    private final AuditService auditService;

    public AutomationRunRecoveryTransaction(
            AutomationRunRepository automationRunRepository,
            GenerationJobRepository generationJobRepository,
            AuditService auditService) {
        this.automationRunRepository = automationRunRepository;
        this.generationJobRepository = generationJobRepository;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recover(Long runId, LocalDateTime now) {
        var run = automationRunRepository.findByIdForUpdate(runId).orElse(null);
        if (run == null || !run.leaseExpired(now)) {
            return false;
        }
        var job = generationJobRepository.findByRunIdForUpdate(runId);

        var detail = new LinkedHashMap<String, Object>();
        detail.put("reasonCode", "RUN_DEADLINE_EXPIRED");
        detail.put("leaseOwner", run.getLeaseOwner() == null ? "none" : run.getLeaseOwner());
        detail.put("leaseExpiresAt", run.getLeaseExpiresAt().toString());
        job.ifPresent(generationJob -> {
            detail.put("jobId", generationJob.getId());
            detail.put("jobStatus", generationJob.getJobStatus().name());
            generationJob.cancelForExpiredRun(EXPIRED_REASON, now);
        });
        run.markFailed(EXPIRED_REASON, now);
        auditService.record(
                AuditActorType.SYSTEM,
                "automation-recovery",
                AuditTargetType.AUTOMATION,
                runId.toString(),
                "AUTOMATION_RUN_RECOVERED_AS_FAILED",
                detail);
        return true;
    }
}
