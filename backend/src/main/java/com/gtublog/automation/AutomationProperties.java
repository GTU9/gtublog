package com.gtublog.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.automation")
public record AutomationProperties(
        @NotNull RunProperties run,
        @NotNull WorkerProperties worker,
        @NotNull RevalidationProperties revalidation) {

    public record RunProperties(
            @NotNull Duration pipelineLeaseDuration,
            @NotNull Duration maxDuration,
            @NotNull Duration recoveryInterval) {

        public RunProperties {
            requirePositive(pipelineLeaseDuration, "pipelineLeaseDuration");
            requirePositive(maxDuration, "maxDuration");
            requirePositive(recoveryInterval, "recoveryInterval");
            if (maxDuration != null
                    && pipelineLeaseDuration != null
                    && maxDuration.compareTo(pipelineLeaseDuration) < 0) {
                throw new IllegalArgumentException("maxDuration must not be shorter than pipelineLeaseDuration.");
            }
        }

        private static void requirePositive(Duration value, String name) {
            if (value != null && value.compareTo(Duration.ofMillis(1)) < 0) {
                throw new IllegalArgumentException(name + " must be at least one millisecond.");
            }
        }
    }

    public record WorkerProperties(
            @NotBlank String sharedToken,
            @NotNull Duration leaseDuration,
            @NotBlank String preferredProvider,
            @NotBlank String schemaVersion) {
    }

    public record RevalidationProperties(
            String baseUrl,
            String sharedSecret,
            @NotNull Duration retryInitialDelay,
            @NotNull Duration retryMaxDelay,
            @NotNull Duration requestTimeout,
            @NotNull Duration leaseDuration,
            @NotNull Duration pollInterval,
            int maxAttempts,
            int batchSize) {

        public RevalidationProperties {
            RunProperties.requirePositive(retryInitialDelay, "retryInitialDelay");
            RunProperties.requirePositive(retryMaxDelay, "retryMaxDelay");
            RunProperties.requirePositive(requestTimeout, "requestTimeout");
            RunProperties.requirePositive(leaseDuration, "leaseDuration");
            RunProperties.requirePositive(pollInterval, "pollInterval");
            if (retryMaxDelay != null && retryInitialDelay != null && retryMaxDelay.compareTo(retryInitialDelay) < 0) {
                throw new IllegalArgumentException("retryMaxDelay must not be shorter than retryInitialDelay.");
            }
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be at least one.");
            }
            if (batchSize < 1) {
                throw new IllegalArgumentException("batchSize must be at least one.");
            }
        }
    }
}
