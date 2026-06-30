package com.gtublog.automation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AutomationPropertiesTests {

    @Test
    void rejectsNonPositiveRunDurations() {
        assertThatThrownBy(() -> new AutomationProperties.RunProperties(
                        Duration.ZERO,
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pipelineLeaseDuration");
    }

    @Test
    void rejectsMaximumDurationShorterThanPipelineLease() {
        assertThatThrownBy(() -> new AutomationProperties.RunProperties(
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDuration");
    }

    @Test
    void rejectsSubMillisecondRecoveryIntervals() {
        assertThatThrownBy(() -> new AutomationProperties.RunProperties(
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(30),
                        Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recoveryInterval");
    }
}
