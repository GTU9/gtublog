package com.gtublog.automation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationPublicationDecisionRepository extends JpaRepository<AutomationPublicationDecision, Long> {

    Optional<AutomationPublicationDecision> findByRunId(Long runId);

    void deleteByRunId(Long runId);
}
