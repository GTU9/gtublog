package com.gtublog.automation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationOriginPairApprovalRepository extends JpaRepository<AutomationOriginPairApproval, Long> {

    List<AutomationOriginPairApproval> findAllByTopicIdOrderByIdDesc(Long topicId);

    List<AutomationOriginPairApproval> findAllByTopicIdAndActiveTrueOrderByGroupLowIdAscGroupHighIdAsc(Long topicId);

    Optional<AutomationOriginPairApproval> findByTopicIdAndGroupLowIdAndGroupHighIdAndActiveTrue(
            Long topicId,
            Long groupLowId,
            Long groupHighId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AutomationOriginPairApproval approval
            SET approval.active = false,
                approval.revision = approval.revision + 1,
                approval.revocationRationale = :reason,
                approval.revokedAt = CURRENT_TIMESTAMP
            WHERE approval.id = :id
                AND approval.revision = :revision
                AND approval.active = true
            """)
    int revokeIfCurrent(@Param("id") Long id, @Param("revision") long revision, @Param("reason") String reason);
}
