package com.gtublog.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationOriginGroupRepository extends JpaRepository<AutomationOriginGroup, Long> {
    List<AutomationOriginGroup> findAllByTopicIdOrderByIdAsc(Long topicId);
    boolean existsByTopicIdAndName(Long topicId, String name);
}
