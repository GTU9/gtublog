package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collection;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "generation_job")
public class GenerationJob extends BaseEntity {

    @Column(name = "job_key", nullable = false, length = 36)
    private String jobKey;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false, length = 32)
    private GenerationJobStatus jobStatus;

    @Column(name = "lease_owner", length = 120)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(name = "prompt_version", nullable = false, length = 64)
    private String promptVersion;

    @Column(name = "schema_version", nullable = false, length = 64)
    private String schemaVersion;

    @Column(name = "worker_id", length = 120)
    private String workerId;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "heartbeat_at")
    private LocalDateTime heartbeatAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "request_payload_json", columnDefinition = "longtext")
    private String requestPayloadJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "result_payload_json", columnDefinition = "longtext")
    private String resultPayloadJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "failure_reason", columnDefinition = "longtext")
    private String failureReason;

    @Column(name = "terminal_submission_id", length = 36)
    private String terminalSubmissionId;

    @Column(name = "terminal_payload_digest", length = 64)
    private String terminalPayloadDigest;

    protected GenerationJob() {
    }

    private GenerationJob(
            String jobKey,
            Long runId,
            GenerationJobStatus jobStatus,
            String leaseOwner,
            LocalDateTime leaseExpiresAt,
            String providerName,
            String promptVersion,
            String schemaVersion,
            String workerId,
            LocalDateTime claimedAt,
            LocalDateTime heartbeatAt,
            LocalDateTime submittedAt,
            String requestPayloadJson,
            String resultPayloadJson,
            String failureReason) {
        this.jobKey = jobKey;
        this.runId = runId;
        this.jobStatus = jobStatus;
        this.leaseOwner = leaseOwner;
        this.leaseExpiresAt = leaseExpiresAt;
        this.providerName = providerName;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.workerId = workerId;
        this.claimedAt = claimedAt;
        this.heartbeatAt = heartbeatAt;
        this.submittedAt = submittedAt;
        this.requestPayloadJson = requestPayloadJson;
        this.resultPayloadJson = resultPayloadJson;
        this.failureReason = failureReason;
    }

    public static GenerationJob enqueue(
            String jobKey,
            Long runId,
            String providerName,
            String promptVersion,
            String schemaVersion,
            String requestPayloadJson) {
        return new GenerationJob(
                jobKey,
                runId,
                GenerationJobStatus.PENDING,
                null,
                null,
                providerName,
                promptVersion,
                schemaVersion,
                null,
                null,
                null,
                null,
                requestPayloadJson,
                null,
                null);
    }

    public Long getId() {
        return super.getId();
    }

    public Long getRunId() {
        return runId;
    }

    public String getJobKey() {
        return jobKey;
    }

    public GenerationJobStatus getJobStatus() {
        return jobStatus;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public LocalDateTime getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getWorkerId() {
        return workerId;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public String getRequestPayloadJson() {
        return requestPayloadJson;
    }

    public String getResultPayloadJson() {
        return resultPayloadJson;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getTerminalSubmissionId() { return terminalSubmissionId; }

    public String getTerminalPayloadDigest() { return terminalPayloadDigest; }

    public void claim(String workerId, LocalDateTime leaseExpiresAt, LocalDateTime now) {
        this.jobStatus = GenerationJobStatus.CLAIMED;
        this.workerId = workerId;
        this.leaseOwner = workerId;
        this.claimedAt = now;
        this.heartbeatAt = now;
        this.leaseExpiresAt = leaseExpiresAt;
        this.failureReason = null;
    }

    public void heartbeat(String workerId, LocalDateTime leaseExpiresAt, LocalDateTime now) {
        requireLeaseOwner(workerId, now);
        this.heartbeatAt = now;
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public void submit(String workerId, String terminalSubmissionId, String terminalPayloadDigest, String resultPayloadJson, LocalDateTime now) {
        requireLeaseOwner(workerId, now);
        this.jobStatus = GenerationJobStatus.SUBMITTED;
        this.resultPayloadJson = resultPayloadJson;
        this.terminalSubmissionId = terminalSubmissionId;
        this.terminalPayloadDigest = terminalPayloadDigest;
        this.submittedAt = now;
        clearLease();
    }

    public void fail(String workerId, String terminalSubmissionId, String terminalPayloadDigest, String failureReason, LocalDateTime now) {
        requireLeaseOwner(workerId, now);
        this.jobStatus = GenerationJobStatus.FAILED;
        this.failureReason = failureReason;
        this.terminalSubmissionId = terminalSubmissionId;
        this.terminalPayloadDigest = terminalPayloadDigest;
        this.submittedAt = now;
        clearLease();
    }

    public void cancelForExpiredRun(String reason, LocalDateTime now) {
        if (jobStatus != GenerationJobStatus.PENDING && jobStatus != GenerationJobStatus.CLAIMED) {
            return;
        }
        this.jobStatus = GenerationJobStatus.CANCELLED;
        this.failureReason = reason;
        this.submittedAt = now;
        clearLease();
    }

    public boolean leaseExpired(LocalDateTime now) {
        return leaseExpiresAt != null && !leaseExpiresAt.isAfter(now);
    }

    public boolean isClaimable(
            LocalDateTime now,
            Collection<String> supportedProviders,
            Collection<String> supportedSchemaVersions) {
        boolean available = jobStatus == GenerationJobStatus.PENDING
                || (jobStatus == GenerationJobStatus.CLAIMED && leaseExpired(now));
        return available
                && supportedProviders.contains(providerName)
                && supportedSchemaVersions.contains(schemaVersion);
    }

    private void requireLeaseOwner(String workerId, LocalDateTime now) {
        if (this.workerId == null || !this.workerId.equals(workerId)) {
            throw new GenerationLeaseLostException("The generation job is claimed by another worker.");
        }
        if (leaseExpired(now)) {
            throw new GenerationLeaseLostException("The generation job lease has expired.");
        }
        if (jobStatus != GenerationJobStatus.CLAIMED) {
            throw new GenerationLeaseLostException("The generation job is not claimable for this action.");
        }
    }

    private void clearLease() {
        this.workerId = null;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
    }
}
