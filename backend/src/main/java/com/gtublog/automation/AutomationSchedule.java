package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "automation_schedule")
public class AutomationSchedule extends BaseEntity {

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "cron_expression", nullable = false, length = 120)
    private String cronExpression;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AutomationScheduleStatus status;

    @Column(name = "misfire_policy", nullable = false, length = 64)
    private String misfirePolicy;

    @Column(name = "next_planned_run_at")
    private LocalDateTime nextPlannedRunAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 32)
    private AutomationScheduleSyncStatus syncStatus;

    @Column(name = "sync_error_message", length = 255)
    private String syncErrorMessage;

    @Column(name = "last_synchronized_at")
    private LocalDateTime lastSynchronizedAt;

    protected AutomationSchedule() {
    }

    private AutomationSchedule(
            Long topicId,
            String name,
            String cronExpression,
            String timezone,
            AutomationScheduleStatus status,
            String misfirePolicy,
            LocalDateTime nextPlannedRunAt,
            AutomationScheduleSyncStatus syncStatus,
            String syncErrorMessage,
            LocalDateTime lastSynchronizedAt) {
        this.topicId = topicId;
        this.name = name;
        this.cronExpression = cronExpression;
        this.timezone = timezone;
        this.status = status;
        this.misfirePolicy = misfirePolicy;
        this.nextPlannedRunAt = nextPlannedRunAt;
        this.syncStatus = syncStatus;
        this.syncErrorMessage = syncErrorMessage;
        this.lastSynchronizedAt = lastSynchronizedAt;
    }

    public static AutomationSchedule create(
            Long topicId,
            String name,
            String cronExpression,
            String timezone,
            AutomationScheduleStatus status,
            String misfirePolicy,
            LocalDateTime nextPlannedRunAt) {
        return new AutomationSchedule(
                topicId,
                name,
                cronExpression,
                timezone,
                status,
                misfirePolicy,
                nextPlannedRunAt,
                AutomationScheduleSyncStatus.OUT_OF_SYNC,
                "Schedule has not been synchronized yet.",
                null);
    }

    public Long getId() {
        return super.getId();
    }

    public Long getTopicId() {
        return topicId;
    }

    public String getName() {
        return name;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getTimezone() {
        return timezone;
    }

    public AutomationScheduleStatus getStatus() {
        return status;
    }

    public String getMisfirePolicy() {
        return misfirePolicy;
    }

    public LocalDateTime getNextPlannedRunAt() {
        return nextPlannedRunAt;
    }

    public AutomationScheduleSyncStatus getSyncStatus() {
        return syncStatus;
    }

    public String getSyncErrorMessage() {
        return syncErrorMessage;
    }

    public LocalDateTime getLastSynchronizedAt() {
        return lastSynchronizedAt;
    }

    public boolean isActive() {
        return status == AutomationScheduleStatus.ACTIVE;
    }

    public void update(
            String name,
            String cronExpression,
            String timezone,
            AutomationScheduleStatus status,
            String misfirePolicy,
            LocalDateTime nextPlannedRunAt) {
        this.name = name;
        this.cronExpression = cronExpression;
        this.timezone = timezone;
        this.status = status;
        this.misfirePolicy = misfirePolicy;
        this.nextPlannedRunAt = nextPlannedRunAt;
    }

    public void markSynchronized(LocalDateTime synchronizedAt) {
        this.syncStatus = AutomationScheduleSyncStatus.SYNCED;
        this.syncErrorMessage = null;
        this.lastSynchronizedAt = synchronizedAt;
    }

    public void markOutOfSync(String errorMessage) {
        this.syncStatus = AutomationScheduleSyncStatus.OUT_OF_SYNC;
        this.syncErrorMessage = errorMessage;
    }
}
