package com.gtublog.automation;

import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import com.gtublog.observability.PlatformMetricsService;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
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
    private final AutomationProperties automationProperties;
    private final Clock clock;

    public AutomationRunLifecycleService(
            AutomationRunRepository automationRunRepository,
            AuditService auditService,
            PlatformMetricsService platformMetricsService,
            AutomationProperties automationProperties,
            Clock clock) {
        this.automationRunRepository = automationRunRepository;
        this.auditService = auditService;
        this.platformMetricsService = platformMetricsService;
        this.automationProperties = automationProperties;
        this.clock = clock;
    }

    @Transactional
    public StartResult start(
            Long topicId,
            Long scheduleId,
            String triggerType,
            String idempotencyKey) {
        return start(topicId, scheduleId, null, triggerType, idempotencyKey);
    }

    @Transactional
    public StartResult start(
            Long topicId,
            Long scheduleId,
            Long retryOfRunId,
            String triggerType,
            String idempotencyKey) {
        var existing = automationRunRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new StartResult(existing.get(), false);
        }

        var now = now();
        var run = automationRunRepository.saveAndFlush(AutomationRun.start(
                UUID.randomUUID().toString(),
                topicId,
                scheduleId,
                retryOfRunId,
                triggerType,
                idempotencyKey,
                DEFAULT_LEASE_OWNER,
                now.plus(automationProperties.run().pipelineLeaseDuration()),
                now));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("triggerType", triggerType);
        detail.put("topicId", topicId);
        if (retryOfRunId != null) {
            detail.put("retryOfRunId", retryOfRunId);
        }
        auditService.record(
                AuditActorType.ADMIN,
                "1",
                AuditTargetType.AUTOMATION,
                run.getId().toString(),
                "AUTOMATION_RUN_STARTED",
                detail);
        return new StartResult(run, true);
    }

    @Transactional
    public void hold(Long runId, String holdReason) {
        var run = automationRunRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new NoSuchElementException("Automation run not found."));
        var now = now();
        run.requireActive(now);
        run.markHeld(holdReason, now);
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

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
