package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "automation_publication_decision")
public class AutomationPublicationDecision extends BaseEntity {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "hold_reason", length = 255)
    private String holdReason;

    @Column(name = "detail_reason", nullable = false, length = 64)
    private String detailReason;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "decision_json", nullable = false, columnDefinition = "longtext")
    private String decisionJson;

    protected AutomationPublicationDecision() {
    }

    private AutomationPublicationDecision(Long runId, String outcome, String holdReason, String detailReason, String decisionJson) {
        this.runId = runId;
        this.outcome = outcome;
        this.holdReason = holdReason;
        this.detailReason = detailReason;
        this.decisionJson = decisionJson;
    }

    public static AutomationPublicationDecision record(
            Long runId,
            String outcome,
            String holdReason,
            String detailReason,
            String decisionJson) {
        return new AutomationPublicationDecision(runId, outcome, holdReason, detailReason, decisionJson);
    }

    public Long getRunId() {
        return runId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getHoldReason() {
        return holdReason;
    }

    public String getDetailReason() {
        return detailReason;
    }

    public String getDecisionJson() {
        return decisionJson;
    }
}
