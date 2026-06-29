package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;

public record GenerationJobHeartbeatRequest(@NotBlank String workerId) {
}
