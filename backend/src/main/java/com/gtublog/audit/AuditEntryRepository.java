package com.gtublog.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    Page<AuditEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
