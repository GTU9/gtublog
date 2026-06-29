package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

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

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    protected GenerationJob() {
    }
}
