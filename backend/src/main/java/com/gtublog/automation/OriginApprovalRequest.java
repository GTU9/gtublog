package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OriginApprovalRequest(@NotBlank @Size(max = 255) String originHost,
                                    @NotNull Long groupId,
                                    @NotBlank @Size(max = 1000) String rationale) {}
