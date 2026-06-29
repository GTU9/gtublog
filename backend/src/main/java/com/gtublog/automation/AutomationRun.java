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
            String triggerType,
            AutomationRunStatus status,
            String idempotencyKey,
            String leaseOwner,
            LocalDateTime leaseExpiresAt) {
        this.runKey = runKey;
        this.topicId = topicId;
        this.scheduleId = scheduleId;
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
        var run = new AutomationRun(
                runKey,
                topicId,
                scheduleId,
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

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void markSucceeded(LocalDateTime completedAt) {
        this.status = AutomationRunStatus.SUCCEEDED;
        this.completedAt = completedAt;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
        this.holdReason = null;
    }

    public void markHeld(String holdReason, LocalDateTime completedAt) {
        this.status = AutomationRunStatus.HELD;
        this.holdReason = holdReason;
        this.completedAt = completedAt;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
    }

    public void markFailed(String holdReason, LocalDateTime completedAt) {
        this.status = AutomationRunStatus.FAILED;
        this.holdReason = holdReason;
        this.completedAt = completedAt;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
    }
}
