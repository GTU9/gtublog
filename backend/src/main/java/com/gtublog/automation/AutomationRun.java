package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "automation_run")
public class AutomationRun extends BaseEntity {

    @Column(name = "run_key", nullable = false, length = 36)
    private String runKey;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "retry_of_run_id")
    private Long retryOfRunId;

    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AutomationRunStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "lease_owner", length = 120)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "hold_reason", length = 255)
    private String holdReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", length = 32)
    private AutomationRunResolutionStatus resolutionStatus;

    @Column(name = "resolution_note", length = 255)
    private String resolutionNote;

    @Column(name = "resolved_post_id")
    private Long resolvedPostId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected AutomationRun() {
    }

    private AutomationRun(
            String runKey,
            Long topicId,
            Long scheduleId,
            Long retryOfRunId,
            String triggerType,
            AutomationRunStatus status,
            String idempotencyKey,
            String leaseOwner,
            LocalDateTime leaseExpiresAt) {
        this.runKey = runKey;
        this.topicId = topicId;
        this.scheduleId = scheduleId;
        this.retryOfRunId = retryOfRunId;
        this.triggerType = triggerType;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.leaseOwner = leaseOwner;
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public static AutomationRun start(
            String runKey,
            Long topicId,
            Long scheduleId,
            String triggerType,
            String idempotencyKey,
            String leaseOwner,
            LocalDateTime leaseExpiresAt,
            LocalDateTime startedAt) {
        return start(
                runKey,
                topicId,
                scheduleId,
                null,
                triggerType,
                idempotencyKey,
                leaseOwner,
                leaseExpiresAt,
                startedAt);
    }

    public static AutomationRun start(
            String runKey,
            Long topicId,
            Long scheduleId,
            Long retryOfRunId,
            String triggerType,
            String idempotencyKey,
            String leaseOwner,
            LocalDateTime leaseExpiresAt,
            LocalDateTime startedAt) {
        var run = new AutomationRun(
                runKey,
                topicId,
                scheduleId,
                retryOfRunId,
                triggerType,
                AutomationRunStatus.RUNNING,
                idempotencyKey,
                leaseOwner,
                leaseExpiresAt);
        run.startedAt = startedAt;
        return run;
    }

    public Long getId() {
        return super.getId();
    }

    public String getRunKey() {
        return runKey;
    }

    public Long getTopicId() {
        return topicId;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public Long getRetryOfRunId() {
        return retryOfRunId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public AutomationRunStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public LocalDateTime getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public String getHoldReason() {
        return holdReason;
    }

    public AutomationRunResolutionStatus getResolutionStatus() {
        return resolutionStatus;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Long getResolvedPostId() {
        return resolvedPostId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void markSucceeded(LocalDateTime completedAt) {
        requireRunning();
        this.status = AutomationRunStatus.SUCCEEDED;
        this.completedAt = completedAt;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
        this.holdReason = null;
    }

    public void markHeld(String holdReason, LocalDateTime completedAt) {
        requireRunning();
        this.status = AutomationRunStatus.HELD;
        this.holdReason = holdReason;
        this.completedAt = completedAt;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
    }

    public void markFailed(String holdReason, LocalDateTime completedAt) {
        requireRunning();
        this.status = AutomationRunStatus.FAILED;
        this.holdReason = holdReason;
        this.completedAt = completedAt;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
    }

    public void markRetried(Long retryRunId) {
        requireTerminal();
        requireUnresolved();
        this.resolutionStatus = AutomationRunResolutionStatus.RETRIED;
        this.resolutionNote = "Retried as run " + retryRunId;
    }

    public void markCancelledByAdmin() {
        this.resolutionStatus = AutomationRunResolutionStatus.CANCELLED;
        this.resolutionNote = AutomationHoldReason.ADMINISTRATOR_CANCELLED;
    }

    public void markOverridePublished(Long postId) {
        requireTerminal();
        requireUnresolved();
        this.resolutionStatus = AutomationRunResolutionStatus.OVERRIDE_PUBLISHED;
        this.resolvedPostId = postId;
        this.resolutionNote = "Published manually as post " + postId;
    }

    public void awaitGeneration(LocalDateTime deadline) {
        requireRunning();
        this.leaseOwner = "generation-worker";
        this.leaseExpiresAt = deadline;
    }

    public void requireActive(LocalDateTime now) {
        if (!isActive(now)) {
            throw new IllegalStateException("The automation run is no longer active.");
        }
    }

    public boolean isActive(LocalDateTime now) {
        return status == AutomationRunStatus.RUNNING
                && leaseExpiresAt != null
                && leaseExpiresAt.isAfter(now);
    }

    public boolean leaseExpired(LocalDateTime now) {
        return status == AutomationRunStatus.RUNNING
                && leaseExpiresAt != null
                && !leaseExpiresAt.isAfter(now);
    }

    private void requireRunning() {
        if (status != AutomationRunStatus.RUNNING) {
            throw new IllegalStateException("The automation run is no longer running.");
        }
    }

    private void requireTerminal() {
        if (status == AutomationRunStatus.RUNNING) {
            throw new IllegalStateException("The automation run is still active.");
        }
    }

    private void requireUnresolved() {
        if (resolutionStatus != null) {
            throw new IllegalStateException("The automation run already has an administrative resolution.");
        }
    }
}
