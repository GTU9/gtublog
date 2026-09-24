package com.gtublog.automation;

import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import com.gtublog.observability.PlatformMetricsService;
import java.time.Clock;
import java.time.LocalDateTime;
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

    private final AutomationTopicRepository automationTopicRepository;
    private final AutomationRunRepository automationRunRepository;
    private final AutomationOriginPairApprovalRepository originPairApprovalRepository;
    private final AutomationRunOriginPairRepository runOriginPairRepository;
    private final AuditService auditService;
    private final PlatformMetricsService platformMetricsService;
    private final AutomationProperties automationProperties;
    private final Clock clock;

    public AutomationRunLifecycleService(
            AutomationTopicRepository automationTopicRepository,
            AutomationRunRepository automationRunRepository,
            AutomationOriginPairApprovalRepository originPairApprovalRepository,
            AutomationRunOriginPairRepository runOriginPairRepository,
            AuditService auditService,
            PlatformMetricsService platformMetricsService,
            AutomationProperties automationProperties,
            Clock clock) {
        this.automationTopicRepository = automationTopicRepository;
        this.automationRunRepository = automationRunRepository;
        this.originPairApprovalRepository = originPairApprovalRepository;
        this.runOriginPairRepository = runOriginPairRepository;
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

        automationTopicRepository.findByIdForUpdate(topicId)
                .orElseThrow(() -> new NoSuchElementException("Automation topic not found."));
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
        var activePairs = originPairApprovalRepository.findAllByTopicIdAndActiveTrueOrderByGroupLowIdAscGroupHighIdAsc(topicId);
        if (!activePairs.isEmpty()) {
            runOriginPairRepository.saveAll(activePairs.stream()
                    .map(pair -> AutomationRunOriginPair.capture(run.getId(), pair, now))
                    .toList());
            runOriginPairRepository.flush();
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("triggerType", triggerType);
        detail.put("topicId", topicId);
        detail.put("originPairApprovalCount", activePairs.size());
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

    @Transactional
    public RetryResult startRetry(Long runId) {
        var previous = automationRunRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new NoSuchElementException("Automation run not found."));
        if (previous.getStatus() != AutomationRunStatus.HELD || previous.getResolutionStatus() != null) {
            throw new IllegalStateException("Only unresolved held automation runs can be retried.");
        }
        var retry = start(
                previous.getTopicId(),
                null,
                runId,
                "RETRY",
                "retry:%d:%s".formatted(runId, UUID.randomUUID()));
        previous.markRetried(retry.run().getId());
        auditService.record(
                AuditActorType.ADMIN,
                "1",
                AuditTargetType.AUTOMATION,
                runId.toString(),
                "AUTOMATION_RUN_RETRIED",
                Map.of("retryRunId", retry.run().getId()));
        return new RetryResult(previous.getTopicId(), retry.run().getId(), retry.run().getLeaseExpiresAt());
    }

    public record RetryResult(Long topicId, Long runId, LocalDateTime leaseExpiresAt) {
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
