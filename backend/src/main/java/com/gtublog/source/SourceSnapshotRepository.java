package com.gtublog.source;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SourceSnapshotRepository extends JpaRepository<SourceSnapshot, Long> {

    long countByAutomationRunId(Long automationRunId);

    List<SourceSnapshot> findAllByAutomationRunIdOrderByCreatedAtAsc(Long automationRunId);

    @Query(value = """
            SELECT COUNT(*)
            FROM post_revision_source_snapshot prss
            JOIN source_snapshot snapshot ON snapshot.id = prss.source_snapshot_id
            JOIN post_revision revision ON revision.id = prss.post_revision_id
            JOIN post post_record ON post_record.id = revision.post_id
            WHERE snapshot.canonical_url IN :canonicalUrls
              AND post_record.status = 'PUBLISHED'
              AND post_record.deleted_at IS NULL
            """, nativeQuery = true)
    long countPublishedCitationsForCanonicalUrls(List<String> canonicalUrls);
}
