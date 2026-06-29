package com.gtublog.source;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceSnapshotRepository extends JpaRepository<SourceSnapshot, Long> {

    long countByAutomationRunId(Long automationRunId);

    List<SourceSnapshot> findAllByAutomationRunIdOrderByCreatedAtAsc(Long automationRunId);
}
