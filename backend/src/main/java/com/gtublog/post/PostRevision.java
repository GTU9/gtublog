package com.gtublog.post;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "post_revision")
public class PostRevision extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "excerpt", nullable = false, length = 500)
    private String excerpt;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "content_markdown", nullable = false, columnDefinition = "longtext")
    private String contentMarkdown;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "content_html", nullable = false, columnDefinition = "longtext")
    private String contentHtml;

    @Column(name = "revision_source", nullable = false, length = 32)
    private String revisionSource;

    @Column(name = "revision_note", length = 255)
    private String revisionNote;

    protected PostRevision() {
    }
}
