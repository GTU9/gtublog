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

    protected AutomationSchedule() {
    }
}
