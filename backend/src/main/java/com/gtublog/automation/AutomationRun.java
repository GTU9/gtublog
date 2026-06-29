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

    @Column(name = "hold_reason", length = 255)
    private String holdReason;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected AutomationRun() {
    }
}
