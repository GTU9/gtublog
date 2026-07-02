package com.gtublog.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationScheduleRepository extends JpaRepository<AutomationSchedule, Long> {

    List<AutomationSchedule> findAllByTopicIdOrderByIdAsc(Long topicId);

    boolean existsByTopicIdAndNameIgnoreCase(Long topicId, String name);

    boolean existsByTopicIdAndNameIgnoreCaseAndIdNot(Long topicId, String name, Long id);
}
