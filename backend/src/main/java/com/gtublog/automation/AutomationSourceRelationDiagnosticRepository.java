package com.gtublog.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationSourceRelationDiagnosticRepository extends JpaRepository<AutomationSourceRelationDiagnostic, Long> {

    List<AutomationSourceRelationDiagnostic> findAllByRunIdOrderByIdAsc(Long runId);

    void deleteByRunId(Long runId);
}
