package com.gtublog.source;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "source_snapshot")
public class SourceSnapshot extends BaseEntity {

    @Column(name = "snapshot_key", nullable = false, length = 36)
    private String snapshotKey;

    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "automation_source_id")
    private Long automationSourceId;

    @Column(name = "automation_run_id")
    private Long automationRunId;

    @Column(name = "source_url", nullable = false, length = 1024)
    private String sourceUrl;

    @Column(name = "canonical_url", length = 1024)
    private String canonicalUrl;

    @Column(name = "origin_host", nullable = false, length = 255)
    private String originHost;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "retrieved_at", nullable = false)
    private LocalDateTime retrievedAt;

    @Column(name = "http_status", nullable = false)
    private Integer httpStatus;

    @Column(name = "etag", length = 255)
    private String etag;

    @Column(name = "last_modified_header", length = 255)
    private String lastModifiedHeader;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_result", nullable = false, length = 32)
    private SourcePolicyResult policyResult;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "body_excerpt", columnDefinition = "longtext")
    private String bodyExcerpt;

    protected SourceSnapshot() {
    }

    private SourceSnapshot(
            String snapshotKey,
            Long topicId,
            Long automationSourceId,
            Long automationRunId,
            String sourceUrl,
            String canonicalUrl,
            String originHost,
            String title,
            LocalDateTime retrievedAt,
            int httpStatus,
            String etag,
            String lastModifiedHeader,
            String contentHash,
            SourcePolicyResult policyResult,
            String bodyExcerpt) {
        this.snapshotKey = snapshotKey;
        this.topicId = topicId;
        this.automationSourceId = automationSourceId;
        this.automationRunId = automationRunId;
        this.sourceUrl = sourceUrl;
        this.canonicalUrl = canonicalUrl;
        this.originHost = originHost;
        this.title = title;
        this.retrievedAt = retrievedAt;
        this.httpStatus = httpStatus;
        this.etag = etag;
        this.lastModifiedHeader = lastModifiedHeader;
        this.contentHash = contentHash;
        this.policyResult = policyResult;
        this.bodyExcerpt = bodyExcerpt;
    }

    public static SourceSnapshot create(
            String snapshotKey,
            Long topicId,
            Long automationSourceId,
            Long automationRunId,
            String sourceUrl,
            String canonicalUrl,
            String originHost,
            String title,
            LocalDateTime retrievedAt,
            int httpStatus,
            String etag,
            String lastModifiedHeader,
            String contentHash,
            SourcePolicyResult policyResult,
            String bodyExcerpt) {
        return new SourceSnapshot(
                snapshotKey,
                topicId,
                automationSourceId,
                automationRunId,
                sourceUrl,
                canonicalUrl,
                originHost,
                title,
                retrievedAt,
                httpStatus,
                etag,
                lastModifiedHeader,
                contentHash,
                policyResult,
                bodyExcerpt);
    }

    public Long getId() {
        return super.getId();
    }

    public Long getAutomationRunId() {
        return automationRunId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public String getOriginHost() {
        return originHost;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getRetrievedAt() {
        return retrievedAt;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getContentHash() {
        return contentHash;
    }

    public SourcePolicyResult getPolicyResult() {
        return policyResult;
    }

    public String getBodyExcerpt() {
        return bodyExcerpt;
    }
}
