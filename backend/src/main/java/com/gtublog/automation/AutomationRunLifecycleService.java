package com.gtublog.automation;

import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import com.gtublog.observability.PlatformMetricsService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationRunLifecycleService {

    private static final String DEFAULT_LEASE_OWNER = "automation-pipeline";

    private final AutomationRunRepository automationRunRepository;
    private final AuditService auditService;
    private final PlatformMetricsService platformMetricsService;

    public AutomationRunLifecycleService(
            AutomationRunRepository automationRunRepository,
            AuditService auditService,
            PlatformMetricsService platformMetricsService) {
        this.automationRunRepository = automationRunRepository;
        this.auditService = auditService;
        this.platformMetricsService = platformMetricsService;
    }

    @Transactional
    public StartResult start(
            Long topicId,
            Long scheduleId,
            String triggerType,
            String idempotencyKey) {
        var existing = automationRunRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new StartResult(existing.get(), false);
        }

        var now = LocalDateTime.now(ZoneOffset.UTC);
        var run = automationRunRepository.saveAndFlush(AutomationRun.start(
                UUID.randomUUID().toString(),
                topicId,
                scheduleId,
                triggerType,
                idempotencyKey,
                DEFAULT_LEASE_OWNER,
                now.plusMinutes(10),
                now));
        auditService.record(
                AuditActorType.ADMIN,
                "1",
                AuditTargetType.AUTOMATION,
                run.getId().toString(),
                "AUTOMATION_RUN_STARTED",
                Map.of("triggerType", triggerType, "topicId", topicId));
        return new StartResult(run, true);
    }

    @Transactional
    public void hold(Long runId, String holdReason) {
        var run = automationRunRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Automation run not found."));
        run.markHeld(holdReason, LocalDateTime.now(ZoneOffset.UTC));
        platformMetricsService.recordPublicationDecision("run_held", holdReason);
        auditService.record(
                AuditActorType.ADMIN,
                "1",
                AuditTargetType.AUTOMATION,
                run.getId().toString(),
                "AUTOMATION_RUN_HELD",
                Map.of("holdReason", holdReason));
    }

    public record StartResult(AutomationRun run, boolean createdNew) {
    }
}
