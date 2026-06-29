package com.gtublog.post;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostRevisionSourceSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostRevisionSourceSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void linkCitation(Long postRevisionId, Long sourceSnapshotId, int citationOrder) {
        jdbcTemplate.update(
                """
                INSERT INTO post_revision_source_snapshot (post_revision_id, source_snapshot_id, citation_order, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6))
                """,
                postRevisionId,
                sourceSnapshotId,
                citationOrder);
    }
}
