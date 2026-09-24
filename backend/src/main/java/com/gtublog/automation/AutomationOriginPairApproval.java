package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "automation_origin_pair_approval")
public class AutomationOriginPairApproval extends BaseEntity {

    @Column(name = "topic_id", nullable = false)
    private Long topicId;
    @Column(name = "group_low_id", nullable = false)
    private Long groupLowId;
    @Column(name = "group_high_id", nullable = false)
    private Long groupHighId;
    @Column(name = "rationale", nullable = false, length = 1000)
    private String rationale;
    @Column(name = "revocation_rationale", length = 1000)
    private String revocationRationale;
    @Column(name = "active", nullable = false)
    private boolean active;
    @Column(name = "revision", nullable = false)
    private long revision;
    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected AutomationOriginPairApproval() {
    }

    private AutomationOriginPairApproval(Long topicId, Long groupLowId, Long groupHighId, String rationale) {
        this.topicId = topicId;
        this.groupLowId = groupLowId;
        this.groupHighId = groupHighId;
        this.rationale = rationale;
        this.active = true;
        this.revision = 1;
        this.approvedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public static AutomationOriginPairApproval approve(Long topicId, Long groupLowId, Long groupHighId, String rationale) {
        return new AutomationOriginPairApproval(topicId, groupLowId, groupHighId, rationale);
    }

    public Long getTopicId() {
        return topicId;
    }

    public Long getGroupLowId() {
        return groupLowId;
    }

    public Long getGroupHighId() {
        return groupHighId;
    }

    public String getRationale() {
        return rationale;
    }

    public String getRevocationRationale() {
        return revocationRationale;
    }

    public boolean isActive() {
        return active;
    }

    public long getRevision() {
        return revision;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}
