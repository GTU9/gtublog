package com.gtublog.automation;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationOriginApprovalRepository extends JpaRepository<AutomationOriginApproval, Long> {
    List<AutomationOriginApproval> findAllBySourceIdOrderByIdDesc(Long sourceId);
    Optional<AutomationOriginApproval> findBySourceIdAndOriginHostAndActiveTrue(Long sourceId, String originHost);
    boolean existsBySourceId(Long sourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT approval FROM AutomationOriginApproval approval WHERE approval.id = :id")
    Optional<AutomationOriginApproval> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("UPDATE AutomationOriginApproval a SET a.active = false, a.revision = a.revision + 1, a.revocationRationale = 'Source URL or type changed.', a.revokedAt = CURRENT_TIMESTAMP WHERE a.sourceId = :sourceId AND a.active = true")
    int revokeAllActiveBySourceId(@Param("sourceId") Long sourceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AutomationOriginApproval a SET a.active = false, a.revision = a.revision + 1, a.revocationRationale = :reason, a.revokedAt = CURRENT_TIMESTAMP WHERE a.id = :id AND a.revision = :revision AND a.active = true")
    int revokeIfCurrent(@Param("id") Long id, @Param("revision") long revision, @Param("reason") String reason);
}
