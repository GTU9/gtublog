package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OriginPairRevokeRequest(
        @NotNull Long revision,
        @NotBlank @Size(max = 1000) String rationale) {
}
