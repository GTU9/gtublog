package com.gtublog.automation;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationTopicRepository extends JpaRepository<AutomationTopic, Long> {

    Optional<AutomationTopic> findBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AutomationTopic t WHERE t.id = :id")
    Optional<AutomationTopic> findByIdForUpdate(@Param("id") Long id);
}
