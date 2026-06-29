package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AutomationScheduleRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String cronExpression,
        @NotBlank @Size(max = 64) String timezone,
        @NotNull AutomationScheduleStatus status,
        @NotBlank @Size(max = 64) String misfirePolicy) {
}
