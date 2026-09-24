package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "automation_run_origin_pair")
public class AutomationRunOriginPair extends BaseEntity {

    @Column(name = "run_id", nullable = false)
    private Long runId;
    @Column(name = "pair_approval_id", nullable = false)
    private Long pairApprovalId;
    @Column(name = "approval_revision", nullable = false)
    private long approvalRevision;
    @Column(name = "group_low_id", nullable = false)
    private Long groupLowId;
    @Column(name = "group_high_id", nullable = false)
    private Long groupHighId;
    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    protected AutomationRunOriginPair() {
    }

    private AutomationRunOriginPair(
            Long runId,
            Long pairApprovalId,
            long approvalRevision,
            Long groupLowId,
            Long groupHighId,
            LocalDateTime capturedAt) {
        this.runId = runId;
        this.pairApprovalId = pairApprovalId;
        this.approvalRevision = approvalRevision;
        this.groupLowId = groupLowId;
        this.groupHighId = groupHighId;
        this.capturedAt = capturedAt;
    }

    public static AutomationRunOriginPair capture(
            Long runId,
            AutomationOriginPairApproval approval,
            LocalDateTime capturedAt) {
        return new AutomationRunOriginPair(
                runId,
                approval.getId(),
                approval.getRevision(),
                approval.getGroupLowId(),
                approval.getGroupHighId(),
                capturedAt);
    }

    public Long getRunId() {
        return runId;
    }

    public Long getPairApprovalId() {
        return pairApprovalId;
    }

    public long getApprovalRevision() {
        return approvalRevision;
    }

    public Long getGroupLowId() {
        return groupLowId;
    }

    public Long getGroupHighId() {
        return groupHighId;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }
}
