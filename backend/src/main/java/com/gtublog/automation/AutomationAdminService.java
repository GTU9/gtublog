package com.gtublog.automation;

import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import com.gtublog.post.SlugService;
import com.gtublog.source.SourcePolicyResult;
import com.gtublog.source.SourceSnapshotRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationAdminService {

    private final AutomationTopicRepository automationTopicRepository;
    private final AutomationSourceRepository automationSourceRepository;
    private final AutomationScheduleRepository automationScheduleRepository;
    private final AutomationRunRepository automationRunRepository;
    private final SourceSnapshotRepository sourceSnapshotRepository;
    private final SlugService slugService;
    private final AuditService auditService;
    private final SourceCollectionService sourceCollectionService;
    private final AutomationScheduleSynchronizer automationScheduleSynchronizer;
    private final GenerationJobService generationJobService;
    private final PublicationOutboxService publicationOutboxService;
    private final SourceUrlPolicy sourceUrlPolicy;
    private final AutomationRunLifecycleService automationRunLifecycleService;

    public AutomationAdminService(
            AutomationTopicRepository automationTopicRepository,
            AutomationSourceRepository automationSourceRepository,
            AutomationScheduleRepository automationScheduleRepository,
            AutomationRunRepository automationRunRepository,
            SourceSnapshotRepository sourceSnapshotRepository,
            SlugService slugService,
            AuditService auditService,
            SourceCollectionService sourceCollectionService,
            AutomationScheduleSynchronizer automationScheduleSynchronizer,
            GenerationJobService generationJobService,
            PublicationOutboxService publicationOutboxService,
            SourceUrlPolicy sourceUrlPolicy,
            AutomationRunLifecycleService automationRunLifecycleService) {
        this.automationTopicRepository = automationTopicRepository;
        this.automationSourceRepository = automationSourceRepository;
        this.automationScheduleRepository = automationScheduleRepository;
        this.automationRunRepository = automationRunRepository;
        this.sourceSnapshotRepository = sourceSnapshotRepository;
        this.slugService = slugService;
        this.auditService = auditService;
        this.sourceCollectionService = sourceCollectionService;
        this.automationScheduleSynchronizer = automationScheduleSynchronizer;
        this.generationJobService = generationJobService;
        this.publicationOutboxService = publicationOutboxService;
        this.sourceUrlPolicy = sourceUrlPolicy;
        this.automationRunLifecycleService = automationRunLifecycleService;
    }

    @Transactional(readOnly = true)
    public List<AutomationTopicResponse> topics() {
        return automationTopicRepository.findAll().stream().map(this::toTopicResponse).toList();
    }

    @Transactional
    public AutomationTopicResponse createTopic(AutomationTopicRequest request) {
        String slug = uniqueTopicSlug(request.slug(), request.name(), null);
        var topic = automationTopicRepository.save(AutomationTopic.create(
                slug,
                request.name(),
                request.promptTemplateVersion(),
                request.publicationEnabled()));
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.AUTOMATION, topic.getId().toString(), "AUTOMATION_TOPIC_CREATED", Map.of("slug", slug));
        return toTopicResponse(topic);
    }

    @Transactional
    public AutomationTopicResponse updateTopic(Long id, AutomationTopicRequest request) {
        var topic = automationTopicRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Automation topic not found."));
        String slug = uniqueTopicSlug(request.slug(), request.name(), id);
        topic.update(slug, request.name(), request.promptTemplateVersion(), request.publicationEnabled());
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.AUTOMATION, topic.getId().toString(), "AUTOMATION_TOPIC_UPDATED", Map.of("slug", slug));
        return toTopicResponse(topic);
    }

    @Transactional(readOnly = true)
    public List<AutomationSourceResponse> sources(Long topicId) {
        requireTopic(topicId);
        return automationSourceRepository.findAllByTopicIdOrderByIdAsc(topicId).stream().map(this::toSourceResponse).toList();
    }

    @Transactional
    public AutomationSourceResponse createSource(Long topicId, AutomationSourceRequest request) {
        requireTopic(topicId);
        var sourceUrl = sourceUrlPolicy.validateStoredUrl(request.sourceUrl()).toASCIIString();
        ensureUniqueSource(topicId, sourceUrl, null);
        var source = automationSourceRepository.save(
                AutomationSource.create(topicId, request.sourceType(), sourceUrl, request.enabled()));
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.AUTOMATION, source.getId().toString(), "AUTOMATION_SOURCE_CREATED", Map.of("topicId", topicId));
        return toSourceResponse(source);
    }

    @Transactional
    public AutomationSourceResponse updateSource(Long sourceId, AutomationSourceRequest request) {
        var source = automationSourceRepository.findById(sourceId).orElseThrow(() -> new NoSuchElementException("Automation source not found."));
        var sourceUrl = sourceUrlPolicy.validateStoredUrl(request.sourceUrl()).toASCIIString();
        ensureUniqueSource(source.getTopicId(), sourceUrl, sourceId);
        source.update(request.sourceType(), sourceUrl, request.enabled());
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.AUTOMATION, source.getId().toString(), "AUTOMATION_SOURCE_UPDATED", Map.of("topicId", source.getTopicId()));
        return toSourceResponse(source);
    }

    @Transactional
    public void deleteSource(Long sourceId) {
        var source = automationSourceRepository.findById(sourceId).orElseThrow(() -> new NoSuchElementException("Automation source not found."));
        if (sourceSnapshotRepository.existsByAutomationSourceId(sourceId)) {
            throw new AutomationConfigurationConflictException(
                    "This source is already referenced by collected evidence. Disable it instead of deleting it.");
        }
        automationSourceRepository.delete(source);
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.AUTOMATION, sourceId.toString(), "AUTOMATION_SOURCE_DELETED", Map.of());
    }

    @Transactional(readOnly = true)
    public List<AutomationScheduleResponse> schedules(Long topicId) {
        requireTopic(topicId);
        return automationScheduleRepository.findAllByTopicIdOrderByIdAsc(topicId).stream().map(this::toScheduleResponse).toList();
    }

    @Transactional
    public AutomationScheduleResponse createSchedule(Long topicId, AutomationScheduleRequest request) {
        requireTopic(topicId);
        validateCron(request.cronExpression(), request.timezone());
        ensureUniqueSchedule(topicId, request.name(), null);
        var schedule = automationScheduleRepository.save(AutomationSchedule.create(
                topicId,
                request.name(),
                request.cronExpression(),
                request.timezone(),
                request.status(),
                normalizeMisfirePolicy(request.misfirePolicy()),
                nextRunAt(request.cronExpression(), request.timezone())));
        synchronizeSchedule(schedule);
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.AUTOMATION, schedule.getId().toString(), "AUTOMATION_SCHEDULE_CREATED", Map.of("topicId", topicId));
        return toScheduleResponse(schedule);
    }

    @Transactional
    public AutomationScheduleResponse updateSchedule(Long scheduleId, AutomationScheduleRequest request) {
        var schedule = automationScheduleRepository.findById(scheduleId).orElseThrow(() -> new NoSuchElementException("Automation schedule not found."));
        validateCron(request.cronExpression(), request.timezone());
        ensureUniqueSchedule(schedule.getTopicId(), request.name(), scheduleId);
        schedule.update(
                request.name(),
                request.cronExpression(),
                request.timezone(),
                request.status(),
                normalizeMisfirePolicy(request.misfirePolicy()),
                nextRunAt(request.cronExpression(), request.timezone()));
        synchronizeSchedule(schedule);
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.AUTOMATION, schedule.getId().toString(), "AUTOMATION_SCHEDULE_UPDATED", Map.of("topicId", schedule.getTopicId()));
        return toScheduleResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(Long scheduleId) {
        var schedule = automationScheduleRepository.findById(scheduleId).orElseThrow(() -> new NoSuchElementException("Automation schedule not found."));
        if (automationRunRepository.existsByScheduleId(scheduleId)) {
            throw new AutomationConfigurationConflictException(
                    "This schedule already has run history. Disable it instead of deleting it.");
        }
        automationScheduleRepository.delete(schedule);
        removeScheduleBestEffort(scheduleId);
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.AUTOMATION, scheduleId.toString(), "AUTOMATION_SCHEDULE_DELETED", Map.of());
    }

    @Transactional(readOnly = true)
    public List<AutomationRunResponse> runs() {
        return automationRunRepository.findTop20ByOrderByCreatedAtDesc().stream().map(this::toRunResponse).toList();
    }

    @Transactional(readOnly = true)
    public AutomationRunDetailResponse runDetail(Long runId) {
        var run = automationRunRepository.findById(runId).orElseThrow(() -> new NoSuchElementException("Automation run not found."));
        var snapshots = sourceSnapshotRepository.findAllByAutomationRunIdOrderByCreatedAtAsc(runId).stream()
                .map(snapshot -> new AutomationRunDetailResponse.SourceSnapshotResponse(
                        snapshot.getId(),
                        snapshot.getSourceUrl(),
                        snapshot.getCanonicalUrl(),
                        snapshot.getOriginHost(),
                        snapshot.getTitle(),
                        snapshot.getHttpStatus(),
                        snapshot.getPolicyResult(),
                        snapshot.getContentHash(),
                        snapshot.getRetrievedAt()))
                .toList();
        return new AutomationRunDetailResponse(toRunResponse(run), snapshots);
    }

    @Transactional(readOnly = true)
    public AutomationDiagnosticsResponse diagnostics() {
        var recentHoldReasons = automationRunRepository.findTop5ByStatusOrderByUpdatedAtDesc(AutomationRunStatus.HELD).stream()
                .map(AutomationRun::getHoldReason)
                .filter(reason -> reason != null && !reason.isBlank())
                .toList();
        return new AutomationDiagnosticsResponse(
                new AutomationDiagnosticsResponse.RunCounts(
                        automationRunRepository.countByStatus(AutomationRunStatus.RUNNING),
                        automationRunRepository.countByStatus(AutomationRunStatus.SUCCEEDED),
                        automationRunRepository.countByStatus(AutomationRunStatus.HELD),
                        automationRunRepository.countByStatus(AutomationRunStatus.FAILED)),
                new AutomationDiagnosticsResponse.JobCounts(
                        generationJobService.countByStatus(GenerationJobStatus.PENDING),
                        generationJobService.countByStatus(GenerationJobStatus.CLAIMED),
                        generationJobService.countByStatus(GenerationJobStatus.SUBMITTED),
                        generationJobService.countByStatus(GenerationJobStatus.FAILED)),
                new AutomationDiagnosticsResponse.OutboxCounts(
                        publicationOutboxService.countByStatus("PENDING"),
                        publicationOutboxService.countByStatus("DELIVERED")),
                sourceSnapshotRepository.countByPolicyResult(SourcePolicyResult.HELD),
                recentHoldReasons,
                LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<AutomationOutboxResponse> outbox() {
        return publicationOutboxService.recentEvents();
    }

    @Transactional
    public void processOutbox() {
        publicationOutboxService.processPendingEvents();
    }

    public AutomationRunResponse triggerManualRun(Long topicId, String idempotencyKey) {
        requireTopic(topicId);
        return executeRun(topicId, null, "MANUAL", idempotencyKey);
    }

    public AutomationRunResponse triggerScheduledRun(Long scheduleId, String idempotencyKey, Instant fireTime) {
        var schedule = automationScheduleRepository.findById(scheduleId).orElseThrow(() -> new NoSuchElementException("Automation schedule not found."));
        String resolvedKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? "schedule:%d:%s".formatted(scheduleId, fireTime == null ? Instant.now().toString() : fireTime.toString())
                : idempotencyKey;
        return executeRun(schedule.getTopicId(), scheduleId, "SCHEDULED", resolvedKey);
    }

    private AutomationRunResponse executeRun(Long topicId, Long scheduleId, String triggerType, String providedIdempotencyKey) {
        String idempotencyKey = providedIdempotencyKey == null || providedIdempotencyKey.isBlank()
                ? triggerType.toLowerCase() + ":" + topicId + ":" + UUID.randomUUID()
                : providedIdempotencyKey;
        var creation = createOrReuseRun(topicId, scheduleId, triggerType, idempotencyKey);
        if (!creation.createdNew()) {
            return toRunResponse(creation.run());
        }

        var enabledSources = automationSourceRepository.findAllByTopicIdAndEnabledTrueOrderByIdAsc(topicId);
        if (enabledSources.isEmpty()) {
            automationRunLifecycleService.hold(creation.run().getId(), "No enabled automation sources are configured for this topic.");
            return runDetail(creation.run().getId()).run();
        }

        var result = sourceCollectionService.collect(topicId, creation.run().getId(), enabledSources);
        if (result.holdReason() == null) {
            var topic = automationTopicRepository.findById(topicId).orElseThrow(() -> new NoSuchElementException("Automation topic not found."));
            generationJobService.enqueueForRun(topic, creation.run(), result.snapshots());
        } else {
            automationRunLifecycleService.hold(creation.run().getId(), result.holdReason());
        }
        return runDetail(creation.run().getId()).run();
    }

    private AutomationRunLifecycleService.StartResult createOrReuseRun(
            Long topicId,
            Long scheduleId,
            String triggerType,
            String idempotencyKey) {
        try {
            return automationRunLifecycleService.start(topicId, scheduleId, triggerType, idempotencyKey);
        } catch (DataIntegrityViolationException exception) {
            var duplicate = automationRunRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
            return new AutomationRunLifecycleService.StartResult(duplicate, false);
        }
    }

    private String uniqueTopicSlug(String providedSlug, String fallbackName, Long currentId) {
        String base = slugService.createSlug(providedSlug == null || providedSlug.isBlank() ? fallbackName : providedSlug);
        String candidate = base;
        int sequence = 2;
        while (true) {
            var existing = automationTopicRepository.findBySlug(candidate).map(AutomationTopic::getId);
            if (existing.isEmpty() || existing.get().equals(currentId)) {
                return candidate;
            }
            candidate = base + "-" + sequence++;
        }
    }

    private void requireTopic(Long topicId) {
        if (!automationTopicRepository.existsById(topicId)) {
            throw new NoSuchElementException("Automation topic not found.");
        }
    }

    private void validateCron(String cronExpression, String timezone) {
        try {
            CronExpression.parse(cronExpression);
            ZoneId.of(timezone);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid cron expression or timezone.");
        }
    }

    private String normalizeMisfirePolicy(String misfirePolicy) {
        if ("DO_NOTHING".equalsIgnoreCase(misfirePolicy)) {
            return "DO_NOTHING";
        }
        return "FIRE_ONCE_NOW";
    }

    private LocalDateTime nextRunAt(String cronExpression, String timezone) {
        var expression = CronExpression.parse(cronExpression);
        var zoneId = ZoneId.of(timezone);
        var next = expression.next(LocalDateTime.now(zoneId));
        return next == null ? null : next.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private void ensureUniqueSource(Long topicId, String sourceUrl, Long sourceId) {
        boolean exists = sourceId == null
                ? automationSourceRepository.existsByTopicIdAndSourceUrl(topicId, sourceUrl)
                : automationSourceRepository.existsByTopicIdAndSourceUrlAndIdNot(topicId, sourceUrl, sourceId);
        if (exists) {
            throw new AutomationConfigurationConflictException("This topic already includes the same source URL.");
        }
    }

    private void ensureUniqueSchedule(Long topicId, String name, Long scheduleId) {
        boolean exists = scheduleId == null
                ? automationScheduleRepository.existsByTopicIdAndNameIgnoreCase(topicId, name)
                : automationScheduleRepository.existsByTopicIdAndNameIgnoreCaseAndIdNot(topicId, name, scheduleId);
        if (exists) {
            throw new AutomationConfigurationConflictException("This topic already includes a schedule with the same name.");
        }
    }

    private void synchronizeSchedule(AutomationSchedule schedule) {
        try {
            automationScheduleSynchronizer.synchronize(schedule);
            schedule.markSynchronized(LocalDateTime.now(ZoneOffset.UTC));
        } catch (IllegalStateException exception) {
            schedule.markOutOfSync("Quartz synchronization failed. Review the schedule and save it again after the scheduler recovers.");
        }
    }

    private void removeScheduleBestEffort(Long scheduleId) {
        try {
            automationScheduleSynchronizer.remove(scheduleId);
        } catch (IllegalStateException ignored) {
            // Best-effort cleanup only; the domain schedule row already reflects the intended state.
        }
    }

    private AutomationTopicResponse toTopicResponse(AutomationTopic topic) {
        return new AutomationTopicResponse(
                topic.getId(),
                topic.getSlug(),
                topic.getName(),
                topic.getPromptTemplateVersion(),
                topic.isPublicationEnabled(),
                topic.getCreatedAt(),
                topic.getUpdatedAt());
    }

    private AutomationSourceResponse toSourceResponse(AutomationSource source) {
        return new AutomationSourceResponse(
                source.getId(),
                source.getTopicId(),
                source.getSourceType(),
                source.getSourceUrl(),
                source.isEnabled(),
                source.getCreatedAt(),
                source.getUpdatedAt());
    }

    private AutomationScheduleResponse toScheduleResponse(AutomationSchedule schedule) {
        return new AutomationScheduleResponse(
                schedule.getId(),
                schedule.getTopicId(),
                schedule.getName(),
                schedule.getCronExpression(),
                schedule.getTimezone(),
                schedule.getStatus(),
                schedule.getMisfirePolicy(),
                schedule.getNextPlannedRunAt(),
                schedule.getSyncStatus(),
                schedule.getSyncErrorMessage(),
                schedule.getLastSynchronizedAt(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt());
    }

    private AutomationRunResponse toRunResponse(AutomationRun run) {
        return new AutomationRunResponse(
                run.getId(),
                run.getRunKey(),
                run.getTopicId(),
                run.getScheduleId(),
                run.getTriggerType(),
                run.getStatus(),
                run.getIdempotencyKey(),
                run.getHoldReason(),
                sourceSnapshotRepository.countByAutomationRunId(run.getId()),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getCreatedAt(),
                run.getUpdatedAt());
    }

}
