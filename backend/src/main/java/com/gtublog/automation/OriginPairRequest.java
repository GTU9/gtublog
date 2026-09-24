package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OriginPairRequest(
        @NotNull Long firstGroupId,
        @NotNull Long secondGroupId,
        @NotBlank @Size(max = 1000) String rationale) {
}
