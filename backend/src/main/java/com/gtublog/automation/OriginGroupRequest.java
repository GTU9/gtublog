package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OriginGroupRequest(@NotBlank @Size(max = 120) String name,
                                 @NotBlank @Size(max = 1000) String rationale) {}
