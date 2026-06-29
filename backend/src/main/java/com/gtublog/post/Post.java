package com.gtublog.post;

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
@Table(name = "post")
public class Post extends BaseEntity {

    @Column(name = "slug", nullable = false, length = 200)
    private String slug;

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
    @Column(name = "status", nullable = false, length = 32)
    private PostStatus status;

    @Column(name = "source_fingerprint", length = 64)
    private String sourceFingerprint;

    @Column(name = "first_published_at")
    private LocalDateTime firstPublishedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Post() {
    }

    private Post(
            String slug,
            String title,
            String excerpt,
            String contentMarkdown,
            String contentHtml,
            String sourceFingerprint) {
        this.slug = slug;
        this.title = title;
        this.excerpt = excerpt;
        this.contentMarkdown = contentMarkdown;
        this.contentHtml = contentHtml;
        this.sourceFingerprint = sourceFingerprint;
        this.status = PostStatus.DRAFT;
    }

    public static Post draft(
            String slug,
            String title,
            String excerpt,
            String contentMarkdown,
            String contentHtml,
            String sourceFingerprint) {
        return new Post(slug, title, excerpt, contentMarkdown, contentHtml, sourceFingerprint);
    }

    public Long getId() {
        return super.getId();
    }

    public String getSlug() {
        return slug;
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

    public PostStatus getStatus() {
        return status;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }

    public LocalDateTime getFirstPublishedAt() {
        return firstPublishedAt;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isPublished() {
        return status == PostStatus.PUBLISHED && deletedAt == null;
    }

    public void revise(String slug, String title, String excerpt, String contentMarkdown, String contentHtml) {
        this.slug = slug;
        this.title = title;
        this.excerpt = excerpt;
        this.contentMarkdown = contentMarkdown;
        this.contentHtml = contentHtml;
    }

    public void publish(LocalDateTime publishedAt) {
        this.status = PostStatus.PUBLISHED;
        if (this.firstPublishedAt == null) {
            this.firstPublishedAt = publishedAt;
        }
        this.archivedAt = null;
        this.deletedAt = null;
    }

    public void archive(LocalDateTime archivedAt) {
        this.status = PostStatus.ARCHIVED;
        this.archivedAt = archivedAt;
        this.deletedAt = null;
    }

    public void markDeleted(LocalDateTime deletedAt) {
        this.status = PostStatus.DELETED;
        this.deletedAt = deletedAt;
    }

    public void restoreToDraft() {
        this.status = PostStatus.DRAFT;
        this.archivedAt = null;
        this.deletedAt = null;
    }
}
