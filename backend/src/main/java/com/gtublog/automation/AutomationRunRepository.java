package com.gtublog.automation;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {

    Optional<AutomationRun> findByIdempotencyKey(String idempotencyKey);

    List<AutomationRun> findTop20ByOrderByCreatedAtDesc();

    boolean existsByScheduleId(Long scheduleId);

    long countByStatus(AutomationRunStatus status);

    List<AutomationRun> findTop5ByStatusOrderByUpdatedAtDesc(AutomationRunStatus status);

    @Query("""
            select run.id
            from AutomationRun run
            where run.status = com.gtublog.automation.AutomationRunStatus.RUNNING
                and run.leaseExpiresAt is not null
                and run.leaseExpiresAt <= :now
            order by run.leaseExpiresAt asc
            """)
    List<Long> findExpiredRunIds(LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from AutomationRun run where run.id = :id")
    Optional<AutomationRun> findByIdForUpdate(Long id);
}
