package com.gtublog.automation;

import jakarta.validation.constraints.Size;

public record AutomationRunRequest(
        @Size(max = 120) String idempotencyKey) {
}
