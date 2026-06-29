package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record GenerationJobClaimRequest(
        @NotBlank String workerId,
        @NotEmpty List<@NotBlank String> supportedProviders,
        @NotEmpty List<@NotBlank String> supportedSchemaVersions) {
}
