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
}
