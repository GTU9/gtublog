package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.automation")
public record AutomationProperties(
        @NotNull WorkerProperties worker,
        @NotNull RevalidationProperties revalidation) {

    public record WorkerProperties(
            @NotBlank String sharedToken,
            @NotNull Duration leaseDuration,
            @NotBlank String preferredProvider,
            @NotBlank String schemaVersion) {
    }

    public record RevalidationProperties(
            String baseUrl,
            @NotBlank String sharedSecret,
            @NotNull Duration retryDelay) {
    }
}
