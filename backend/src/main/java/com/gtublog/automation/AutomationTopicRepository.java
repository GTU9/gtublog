package com.gtublog.automation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationTopicRepository extends JpaRepository<AutomationTopic, Long> {

    Optional<AutomationTopic> findBySlug(String slug);
}
