package com.gtublog.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AutomationSourceRepository extends JpaRepository<AutomationSource, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AutomationSource s WHERE s.id = :id")
    Optional<AutomationSource> findLockedById(@Param("id") Long id);

    List<AutomationSource> findAllByTopicIdOrderByIdAsc(Long topicId);

    List<AutomationSource> findAllByTopicIdAndEnabledTrueOrderByIdAsc(Long topicId);

    boolean existsByTopicIdAndSourceUrl(Long topicId, String sourceUrl);

    boolean existsByTopicIdAndSourceUrlAndIdNot(Long topicId, String sourceUrl, Long id);
}
