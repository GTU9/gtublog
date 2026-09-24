package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "automation_source_relation_diagnostic")
public class AutomationSourceRelationDiagnostic extends BaseEntity {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "left_snapshot_id", nullable = false)
    private Long leftSnapshotId;

    @Column(name = "right_snapshot_id", nullable = false)
    private Long rightSnapshotId;

    @Column(name = "relation_type", nullable = false, length = 64)
    private String relationType;

    @Column(name = "evidence_value", length = 1024)
    private String evidenceValue;

    protected AutomationSourceRelationDiagnostic() {
    }

    private AutomationSourceRelationDiagnostic(
            Long runId,
            Long leftSnapshotId,
            Long rightSnapshotId,
            String relationType,
            String evidenceValue) {
        this.runId = runId;
        this.leftSnapshotId = leftSnapshotId;
        this.rightSnapshotId = rightSnapshotId;
        this.relationType = relationType;
        this.evidenceValue = evidenceValue;
    }

    public static AutomationSourceRelationDiagnostic record(
            Long runId,
            Long leftSnapshotId,
            Long rightSnapshotId,
            String relationType,
            String evidenceValue) {
        return new AutomationSourceRelationDiagnostic(runId, leftSnapshotId, rightSnapshotId, relationType, evidenceValue);
    }

    public Long getLeftSnapshotId() {
        return leftSnapshotId;
    }

    public Long getRightSnapshotId() {
        return rightSnapshotId;
    }

    public String getRelationType() {
        return relationType;
    }

    public String getEvidenceValue() {
        return evidenceValue;
    }
}
