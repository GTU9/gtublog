package com.gtublog.post;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
class PostQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    PostQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<PostArchiveEntryResponse> archiveEntries() {
        return jdbcTemplate.query(
                """
                SELECT YEAR(first_published_at) AS published_year,
                       MONTH(first_published_at) AS published_month,
                       COUNT(*) AS post_count
                FROM post
                WHERE status = 'PUBLISHED'
                  AND deleted_at IS NULL
                  AND first_published_at IS NOT NULL
                GROUP BY YEAR(first_published_at), MONTH(first_published_at)
                ORDER BY published_year DESC, published_month DESC
                """,
                (rs, rowNum) -> new PostArchiveEntryResponse(
                        rs.getInt("published_year"),
                        rs.getInt("published_month"),
                        rs.getLong("post_count")));
    }

    void replaceCategories(Long postId, List<Long> categoryIds) {
        jdbcTemplate.update("DELETE FROM post_category WHERE post_id = ?", postId);
        for (var categoryId : categoryIds) {
            jdbcTemplate.update("INSERT INTO post_category (post_id, category_id) VALUES (?, ?)", postId, categoryId);
        }
    }

    void replaceTags(Long postId, List<Long> tagIds) {
        jdbcTemplate.update("DELETE FROM post_tag WHERE post_id = ?", postId);
        for (var tagId : tagIds) {
            jdbcTemplate.update("INSERT INTO post_tag (post_id, tag_id) VALUES (?, ?)", postId, tagId);
        }
    }

    List<Long> categoryIdsForPost(Long postId) {
        return jdbcTemplate.queryForList(
                "SELECT category_id FROM post_category WHERE post_id = ? ORDER BY category_id",
                Long.class,
                postId);
    }

    List<Long> tagIdsForPost(Long postId) {
        return jdbcTemplate.queryForList(
                "SELECT tag_id FROM post_tag WHERE post_id = ? ORDER BY tag_id",
                Long.class,
                postId);
    }

    List<PostSummaryProjection> searchPublished(String query, int limit, int offset) {
        return jdbcTemplate.query(
                """
                SELECT p.id, p.slug, p.title, p.excerpt, p.status, p.first_published_at,
                       COALESCE(v.view_count, 0) AS view_count
                FROM post p
                LEFT JOIN post_view_counter v ON v.post_id = p.id
                WHERE p.status = 'PUBLISHED'
                  AND p.deleted_at IS NULL
                  AND MATCH(p.title, p.excerpt, p.content_markdown) AGAINST (? IN NATURAL LANGUAGE MODE)
                ORDER BY p.first_published_at DESC, p.id DESC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> mapProjection(rs),
                query,
                limit,
                offset);
    }

    long countSearchPublished(String query) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM post p
                WHERE p.status = 'PUBLISHED'
                  AND p.deleted_at IS NULL
                  AND MATCH(p.title, p.excerpt, p.content_markdown) AGAINST (? IN NATURAL LANGUAGE MODE)
                """,
                Long.class,
                query);
    }

    List<PostSummaryProjection> findPublishedByCategory(String slug, int limit, int offset) {
        return jdbcTemplate.query(
                """
                SELECT p.id, p.slug, p.title, p.excerpt, p.status, p.first_published_at,
                       COALESCE(v.view_count, 0) AS view_count
                FROM post p
                JOIN post_category pc ON pc.post_id = p.id
                JOIN category c ON c.id = pc.category_id
                LEFT JOIN post_view_counter v ON v.post_id = p.id
                WHERE p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND c.slug = ?
                ORDER BY p.first_published_at DESC, p.id DESC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> mapProjection(rs),
                slug,
                limit,
                offset);
    }

    long countPublishedByCategory(String slug) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM post p
                JOIN post_category pc ON pc.post_id = p.id
                JOIN category c ON c.id = pc.category_id
                WHERE p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND c.slug = ?
                """,
                Long.class,
                slug);
    }

    List<PostSummaryProjection> findPublishedByTag(String slug, int limit, int offset) {
        return jdbcTemplate.query(
                """
                SELECT p.id, p.slug, p.title, p.excerpt, p.status, p.first_published_at,
                       COALESCE(v.view_count, 0) AS view_count
                FROM post p
                JOIN post_tag pt ON pt.post_id = p.id
                JOIN tag t ON t.id = pt.tag_id
                LEFT JOIN post_view_counter v ON v.post_id = p.id
                WHERE p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND t.slug = ?
                ORDER BY p.first_published_at DESC, p.id DESC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> mapProjection(rs),
                slug,
                limit,
                offset);
    }

    long countPublishedByTag(String slug) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM post p
                JOIN post_tag pt ON pt.post_id = p.id
                JOIN tag t ON t.id = pt.tag_id
                WHERE p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND t.slug = ?
                """,
                Long.class,
                slug);
    }

    List<PostSummaryProjection> relatedPublishedPosts(Long postId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT p.id, p.slug, p.title, p.excerpt, p.status, p.first_published_at,
                       COALESCE(v.view_count, 0) AS view_count
                FROM post p
                LEFT JOIN post_view_counter v ON v.post_id = p.id
                LEFT JOIN post_category pc ON pc.post_id = p.id
                LEFT JOIN post_tag pt ON pt.post_id = p.id
                WHERE p.id <> ?
                  AND p.status = 'PUBLISHED'
                  AND p.deleted_at IS NULL
                  AND (
                      pc.category_id IN (SELECT category_id FROM post_category WHERE post_id = ?)
                      OR pt.tag_id IN (SELECT tag_id FROM post_tag WHERE post_id = ?)
                  )
                ORDER BY p.first_published_at DESC, p.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapProjection(rs),
                postId,
                postId,
                postId,
                limit);
    }

    Map<Long, List<String>> categoryNamesByPostIds(List<Long> postIds) {
        return postIds.isEmpty()
                ? Map.of()
                : jdbcTemplate.query(
                        """
                        SELECT pc.post_id, c.name
                        FROM post_category pc
                        JOIN category c ON c.id = pc.category_id
                        WHERE pc.post_id IN (%s)
                        ORDER BY c.name
                        """.formatted(placeholders(postIds.size())),
                        ps -> {
                            for (int index = 0; index < postIds.size(); index++) {
                                ps.setLong(index + 1, postIds.get(index));
                            }
                        },
                        (ResultSetExtractor<Map<Long, List<String>>>) this::aggregateNamesByPostId);
    }

    Map<Long, List<String>> tagNamesByPostIds(List<Long> postIds) {
        return postIds.isEmpty()
                ? Map.of()
                : jdbcTemplate.query(
                        """
                        SELECT pt.post_id, t.name
                        FROM post_tag pt
                        JOIN tag t ON t.id = pt.tag_id
                        WHERE pt.post_id IN (%s)
                        ORDER BY t.name
                        """.formatted(placeholders(postIds.size())),
                        ps -> {
                            for (int index = 0; index < postIds.size(); index++) {
                                ps.setLong(index + 1, postIds.get(index));
                            }
                        },
                        (ResultSetExtractor<Map<Long, List<String>>>) this::aggregateNamesByPostId);
    }

    private PostSummaryProjection mapProjection(ResultSet rs) throws SQLException {
        return new PostSummaryProjection(
                rs.getLong("id"),
                rs.getString("slug"),
                rs.getString("title"),
                rs.getString("excerpt"),
                PostStatus.valueOf(rs.getString("status")),
                rs.getObject("first_published_at", LocalDateTime.class),
                rs.getLong("view_count"));
    }

    private String placeholders(int size) {
        return String.join(", ", java.util.Collections.nCopies(size, "?"));
    }

    private Map<Long, List<String>> aggregateNamesByPostId(ResultSet rs) throws SQLException {
        Map<Long, List<String>> namesByPostId = new LinkedHashMap<>();
        while (rs.next()) {
            namesByPostId
                    .computeIfAbsent(rs.getLong("post_id"), ignored -> new ArrayList<>())
                    .add(rs.getString("name"));
        }
        return namesByPostId;
    }

    record PostSummaryProjection(
            Long id,
            String slug,
            String title,
            String excerpt,
            PostStatus status,
            LocalDateTime firstPublishedAt,
            long viewCount) {
    }
}
