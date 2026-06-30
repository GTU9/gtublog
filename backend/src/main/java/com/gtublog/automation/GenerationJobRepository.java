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

    interface ClaimCandidate {
        Long getJobId();

        Long getRunId();
    }

    Optional<GenerationJob> findByRunId(Long runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from GenerationJob job where job.runId = :runId")
    Optional<GenerationJob> findByRunIdForUpdate(Long runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from GenerationJob job where job.id = :id")
    Optional<GenerationJob> findByIdForUpdate(Long id);

    @Query("select job.runId from GenerationJob job where job.id = :id")
    Optional<Long> findRunIdById(Long id);

    long countByRunId(Long runId);

    long countByJobStatus(GenerationJobStatus status);

    @Query("""
            select job.id as jobId, job.runId as runId
            from GenerationJob job
            where (job.jobStatus = com.gtublog.automation.GenerationJobStatus.PENDING
                or (job.jobStatus = com.gtublog.automation.GenerationJobStatus.CLAIMED
                    and job.leaseExpiresAt is not null
                    and job.leaseExpiresAt <= :now))
                and job.providerName in :providers
                and job.schemaVersion in :schemaVersions
                and exists (
                    select run.id
                    from AutomationRun run
                    where run.id = job.runId
                        and run.status = com.gtublog.automation.AutomationRunStatus.RUNNING
                        and run.leaseExpiresAt > :now
                )
            order by job.createdAt asc
            """)
    List<ClaimCandidate> findClaimableJobs(
            LocalDateTime now,
            Collection<String> providers,
            Collection<String> schemaVersions,
            Pageable pageable);
}
