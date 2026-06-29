package com.gtublog.automation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {

    Optional<AutomationRun> findByIdempotencyKey(String idempotencyKey);

    List<AutomationRun> findTop20ByOrderByCreatedAtDesc();

    long countByStatus(AutomationRunStatus status);

    List<AutomationRun> findTop5ByStatusOrderByUpdatedAtDesc(AutomationRunStatus status);
}
