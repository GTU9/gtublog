package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AutomationTopicRequest(
        @Size(max = 120) String slug,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 64) String promptTemplateVersion,
        boolean publicationEnabled) {
}
