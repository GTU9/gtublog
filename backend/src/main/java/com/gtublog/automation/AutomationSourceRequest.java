package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AutomationSourceRequest(
        @NotNull AutomationSourceType sourceType,
        @NotBlank @Size(max = 512) String sourceUrl,
        boolean enabled) {
}
