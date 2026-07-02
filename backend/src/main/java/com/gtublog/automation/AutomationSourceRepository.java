package com.gtublog.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationSourceRepository extends JpaRepository<AutomationSource, Long> {

    List<AutomationSource> findAllByTopicIdOrderByIdAsc(Long topicId);

    List<AutomationSource> findAllByTopicIdAndEnabledTrueOrderByIdAsc(Long topicId);

    boolean existsByTopicIdAndSourceUrl(Long topicId, String sourceUrl);

    boolean existsByTopicIdAndSourceUrlAndIdNot(Long topicId, String sourceUrl, Long id);
}
