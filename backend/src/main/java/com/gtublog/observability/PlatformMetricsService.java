package com.gtublog.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlatformMetricsService {

    private final MeterRegistry meterRegistry;

    public PlatformMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAuthEvent(String action) {
        meterRegistry.counter("gtublog.auth.events", "action", normalize(action)).increment();
    }

    public void recordGenerationJobEvent(String action) {
        meterRegistry.counter("gtublog.automation.generation.jobs", "action", normalize(action)).increment();
    }

    public void recordRunRecovery(String reason) {
        meterRegistry.counter("gtublog.automation.run.recoveries", "reason", normalize(reason)).increment();
    }

    public void recordSourceSnapshot(String result, String sourceType) {
        meterRegistry.counter(
                        "gtublog.automation.source.snapshots",
                        "result",
                        normalize(result),
                        "source_type",
                        normalize(sourceType))
                .increment();
    }

    public void recordPublicationDecision(String outcome, String reason) {
        meterRegistry.counter(
                        "gtublog.automation.publication.decisions",
                        "outcome",
                        normalize(outcome),
                        "reason",
                        normalize(reason))
                .increment();
    }

    public void recordPublicationDuration(Duration duration, String outcome) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        Timer.builder("gtublog.automation.publication.duration")
                .tags(List.of(Tag.of("outcome", normalize(outcome))))
                .register(meterRegistry)
                .record(duration);
    }

    public void recordOutboxDelivery(String status) {
        meterRegistry.counter("gtublog.automation.outbox.deliveries", "status", normalize(status)).increment();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }
}
