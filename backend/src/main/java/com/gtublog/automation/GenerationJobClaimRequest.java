package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GenerationJobClaimRequest(
        @NotBlank @Size(max = 120) String workerId,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 64) String> supportedProviders,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 64) String> supportedSchemaVersions) {

    public GenerationJobClaimRequest {
        if (supportedSchemaVersions != null && !supportedSchemaVersions.contains("automation-job-v2")) {
            throw new IllegalArgumentException("The worker must support automation-job-v2.");
        }
    }
}
