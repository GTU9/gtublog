package com.gtublog.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRunOriginPairRepository extends JpaRepository<AutomationRunOriginPair, Long> {

    List<AutomationRunOriginPair> findAllByRunIdOrderByIdAsc(Long runId);
}
