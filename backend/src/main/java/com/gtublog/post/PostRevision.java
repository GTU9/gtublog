package com.gtublog.post;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "revision_source", nullable = false, length = 32)
    private RevisionSource revisionSource;

    @Column(name = "revision_note", length = 255)
    private String revisionNote;

    protected PostRevision() {
    }

    private PostRevision(
            Long postId,
            Integer revisionNumber,
            String title,
            String excerpt,
            String contentMarkdown,
            String contentHtml,
            RevisionSource revisionSource,
            String revisionNote) {
        this.postId = postId;
        this.revisionNumber = revisionNumber;
        this.title = title;
        this.excerpt = excerpt;
        this.contentMarkdown = contentMarkdown;
        this.contentHtml = contentHtml;
        this.revisionSource = revisionSource;
        this.revisionNote = revisionNote;
    }

    public static PostRevision create(
            Long postId,
            Integer revisionNumber,
            String title,
            String excerpt,
            String contentMarkdown,
            String contentHtml,
            RevisionSource revisionSource,
            String revisionNote) {
        return new PostRevision(
                postId,
                revisionNumber,
                title,
                excerpt,
                contentMarkdown,
                contentHtml,
                revisionSource,
                revisionNote);
    }

    public Long getId() {
        return super.getId();
    }

    public Long getPostId() {
        return postId;
    }

    public Integer getRevisionNumber() {
        return revisionNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public RevisionSource getRevisionSource() {
        return revisionSource;
    }

    public String getRevisionNote() {
        return revisionNote;
    }
}
