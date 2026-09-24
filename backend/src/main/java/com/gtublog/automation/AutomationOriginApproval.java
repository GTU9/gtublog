package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "automation_origin_approval")
public class AutomationOriginApproval extends BaseEntity {
    @Column(name = "source_id", nullable = false)
    private Long sourceId;
    @Column(name = "origin_host", nullable = false, length = 255)
    private String originHost;
    @Column(name = "group_id", nullable = false)
    private Long groupId;
    @Column(name = "approved_source_url", nullable = false, length = 512)
    private String approvedSourceUrl;
    @Enumerated(EnumType.STRING)
    @Column(name = "approved_source_type", nullable = false, length = 32)
    private AutomationSourceType approvedSourceType;
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

    protected AutomationOriginApproval() {}

    private AutomationOriginApproval(AutomationSource source, String originHost, Long groupId, String rationale) {
        this.sourceId = source.getId();
        this.originHost = originHost;
        this.groupId = groupId;
        this.approvedSourceUrl = source.getSourceUrl();
        this.approvedSourceType = source.getSourceType();
        this.rationale = rationale;
        this.active = true;
        this.revision = 1;
        this.approvedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public static AutomationOriginApproval approve(AutomationSource source, String originHost, Long groupId, String rationale) {
        return new AutomationOriginApproval(source, originHost, groupId, rationale);
    }

    public void revoke(String reason) {
        if (!active) {
            throw new AutomationConfigurationConflictException("Origin approval is already revoked.");
        }
        this.active = false;
        this.revision++;
        this.revocationRationale = reason;
        this.revokedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public boolean matches(AutomationSource source, String host) {
        return active && sourceId.equals(source.getId()) && originHost.equals(host)
                && approvedSourceUrl.equals(source.getSourceUrl()) && approvedSourceType == source.getSourceType();
    }

    public Long getSourceId() { return sourceId; }
    public String getOriginHost() { return originHost; }
    public Long getGroupId() { return groupId; }
    public String getApprovedSourceUrl() { return approvedSourceUrl; }
    public AutomationSourceType getApprovedSourceType() { return approvedSourceType; }
    public String getRationale() { return rationale; }
    public String getRevocationRationale() { return revocationRationale; }
    public boolean isActive() { return active; }
    public long getRevision() { return revision; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
}
