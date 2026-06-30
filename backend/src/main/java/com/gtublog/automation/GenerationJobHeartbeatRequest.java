package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerationJobHeartbeatRequest(@NotBlank @Size(max = 120) String workerId) {
}
