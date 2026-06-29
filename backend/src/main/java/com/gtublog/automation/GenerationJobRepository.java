package com.gtublog.automation;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByRunId(Long runId);

    long countByRunId(Long runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job
            from GenerationJob job
            where (job.jobStatus = com.gtublog.automation.GenerationJobStatus.PENDING
                or (job.jobStatus = com.gtublog.automation.GenerationJobStatus.CLAIMED
                    and job.leaseExpiresAt is not null
                    and job.leaseExpiresAt < :now))
                and job.providerName in :providers
                and job.schemaVersion in :schemaVersions
            order by job.createdAt asc
            """)
    List<GenerationJob> findClaimableJobs(
            LocalDateTime now,
            Collection<String> providers,
            Collection<String> schemaVersions,
            Pageable pageable);
}
