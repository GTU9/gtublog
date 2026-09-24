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

    @Column(name = "source_url", nullable = false, length = 512)
    private String sourceUrl;

    @Column(name = "fetched_url", length = 1024)
    private String fetchedUrl;

    @Column(name = "source_feed_url", length = 512)
    private String sourceFeedUrl;

    @Column(name = "source_feed_entry_key", length = 512)
    private String sourceFeedEntryKey;

    @Column(name = "canonical_url", length = 1024)
    private String canonicalUrl;

    @Column(name = "origin_host", nullable = false, length = 255)
    private String originHost;

    @Column(name = "origin_approval_id")
    private Long originApprovalId;

    @Column(name = "origin_approval_revision")
    private Long originApprovalRevision;

    @Column(name = "origin_group_id")
    private Long originGroupId;

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

    @Column(name = "body_text_hash", length = 64)
    private String bodyTextHash;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "article_evidence_text", columnDefinition = "longtext")
    private String articleEvidenceText;

    @Column(name = "article_evidence_hash", length = 64)
    private String articleEvidenceHash;

    @Column(name = "article_evidence_truncated", nullable = false)
    private boolean articleEvidenceTruncated;

    @Column(name = "lineage_extraction_status", nullable = false, length = 32)
    private String lineageExtractionStatus = "UNKNOWN";

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "explicit_upstream_urls_json", columnDefinition = "longtext")
    private String explicitUpstreamUrlsJson;

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
            String fetchedUrl,
            String sourceFeedUrl,
            String sourceFeedEntryKey,
            String canonicalUrl,
            String originHost,
            String title,
            LocalDateTime retrievedAt,
            int httpStatus,
            String etag,
            String lastModifiedHeader,
            String contentHash,
            String bodyTextHash,
            String lineageExtractionStatus,
            String explicitUpstreamUrlsJson,
            SourcePolicyResult policyResult,
            String bodyExcerpt,
            String articleEvidenceText,
            String articleEvidenceHash,
            boolean articleEvidenceTruncated) {
        this.snapshotKey = snapshotKey;
        this.topicId = topicId;
        this.automationSourceId = automationSourceId;
        this.automationRunId = automationRunId;
        this.sourceUrl = sourceUrl;
        this.fetchedUrl = fetchedUrl;
        this.sourceFeedUrl = sourceFeedUrl;
        this.sourceFeedEntryKey = sourceFeedEntryKey;
        this.canonicalUrl = canonicalUrl;
        this.originHost = originHost;
        this.title = title;
        this.retrievedAt = retrievedAt;
        this.httpStatus = httpStatus;
        this.etag = etag;
        this.lastModifiedHeader = lastModifiedHeader;
        this.contentHash = contentHash;
        this.bodyTextHash = bodyTextHash;
        this.lineageExtractionStatus = lineageExtractionStatus == null ? "UNKNOWN" : lineageExtractionStatus;
        this.explicitUpstreamUrlsJson = explicitUpstreamUrlsJson;
        this.policyResult = policyResult;
        this.bodyExcerpt = bodyExcerpt;
        this.articleEvidenceText = articleEvidenceText;
        this.articleEvidenceHash = articleEvidenceHash;
        this.articleEvidenceTruncated = articleEvidenceTruncated;
    }

    public static SourceSnapshot create(
            String snapshotKey,
            Long topicId,
            Long automationSourceId,
            Long automationRunId,
            String sourceUrl,
            String fetchedUrl,
            String canonicalUrl,
            String originHost,
            String title,
            LocalDateTime retrievedAt,
            int httpStatus,
            String etag,
            String lastModifiedHeader,
            String contentHash,
            String bodyTextHash,
            String lineageExtractionStatus,
            String explicitUpstreamUrlsJson,
            SourcePolicyResult policyResult,
            String bodyExcerpt,
            String articleEvidenceText,
            String articleEvidenceHash,
            boolean articleEvidenceTruncated) {
        return new SourceSnapshot(
                snapshotKey,
                topicId,
                automationSourceId,
                automationRunId,
                sourceUrl,
                fetchedUrl,
                null,
                null,
                canonicalUrl,
                originHost,
                title,
                retrievedAt,
                httpStatus,
                etag,
                lastModifiedHeader,
                contentHash,
                bodyTextHash,
                lineageExtractionStatus,
                explicitUpstreamUrlsJson,
                policyResult,
                bodyExcerpt,
                articleEvidenceText,
                articleEvidenceHash,
                articleEvidenceTruncated);
    }

    public static SourceSnapshot createFeedEntrySnapshot(
            String snapshotKey,
            Long topicId,
            Long automationSourceId,
            Long automationRunId,
            String sourceUrl,
            String fetchedUrl,
            String sourceFeedUrl,
            String sourceFeedEntryKey,
            String canonicalUrl,
            String originHost,
            String title,
            LocalDateTime retrievedAt,
            int httpStatus,
            String etag,
            String lastModifiedHeader,
            String contentHash,
            String bodyTextHash,
            String lineageExtractionStatus,
            String explicitUpstreamUrlsJson,
            SourcePolicyResult policyResult,
            String bodyExcerpt,
            String articleEvidenceText,
            String articleEvidenceHash,
            boolean articleEvidenceTruncated) {
        return new SourceSnapshot(
                snapshotKey,
                topicId,
                automationSourceId,
                automationRunId,
                sourceUrl,
                fetchedUrl,
                sourceFeedUrl,
                sourceFeedEntryKey,
                canonicalUrl,
                originHost,
                title,
                retrievedAt,
                httpStatus,
                etag,
                lastModifiedHeader,
                contentHash,
                bodyTextHash,
                lineageExtractionStatus,
                explicitUpstreamUrlsJson,
                policyResult,
                bodyExcerpt,
                articleEvidenceText,
                articleEvidenceHash,
                articleEvidenceTruncated);
    }

    public Long getId() {
        return super.getId();
    }

    public Long getAutomationRunId() {
        return automationRunId;
    }

    public Long getAutomationSourceId() {
        return automationSourceId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getFetchedUrl() {
        return fetchedUrl;
    }

    public String getSourceFeedUrl() {
        return sourceFeedUrl;
    }

    public String getSourceFeedEntryKey() {
        return sourceFeedEntryKey;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public String getOriginHost() {
        return originHost;
    }

    public void captureOriginApproval(Long approvalId, long revision, Long groupId) {
        if (originApprovalId != null || policyResult != SourcePolicyResult.ALLOWED || httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalStateException("Origin approval can only be captured once on a successful article snapshot.");
        }
        originApprovalId = approvalId;
        originApprovalRevision = revision;
        originGroupId = groupId;
    }

    public Long getOriginApprovalId() { return originApprovalId; }
    public Long getOriginApprovalRevision() { return originApprovalRevision; }
    public Long getOriginGroupId() { return originGroupId; }

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

    public String getBodyTextHash() {
        return bodyTextHash;
    }

    public String getArticleEvidenceText() {
        return articleEvidenceText;
    }

    public String getArticleEvidenceHash() {
        return articleEvidenceHash;
    }

    public boolean getArticleEvidenceTruncated() {
        return articleEvidenceTruncated;
    }

    public String getLineageExtractionStatus() {
        return lineageExtractionStatus;
    }

    public String getExplicitUpstreamUrlsJson() {
        return explicitUpstreamUrlsJson;
    }

    public SourcePolicyResult getPolicyResult() {
        return policyResult;
    }

    public String getBodyExcerpt() {
        return bodyExcerpt;
    }
}
