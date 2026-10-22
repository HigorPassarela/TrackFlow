package dev.higor.trackflow.api.version.application;

import dev.higor.trackflow.api.version.domain.TrackVersion;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Processing outcomes as Micrometer meters, recorded only for results that were applied, so a
 * duplicated message never counts twice. Duration goes from confirmation to the final status: the
 * wait a producer actually experiences, queue time included.
 */
@Component
class ProcessingMetrics {

    static final String DURATION = "trackflow.processing.duration";
    static final String RESULTS = "trackflow.processing.results";

    private final MeterRegistry registry;
    private final Timer duration;

    ProcessingMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.duration = Timer.builder(DURATION)
                .description("Time from upload confirmation to ready or failed")
                .publishPercentileHistogram()
                .register(registry);
    }

    void succeeded(TrackVersion version, Instant at) {
        recordDuration(version, at);
        counter("succeeded", "none").increment();
    }

    void failed(TrackVersion version, String reason, Instant at) {
        recordDuration(version, at);
        counter("failed", reason).increment();
    }

    private void recordDuration(TrackVersion version, Instant at) {
        if (version.getConfirmedAt() != null) {
            duration.record(Duration.between(version.getConfirmedAt(), at));
        }
    }

    private Counter counter(String outcome, String reason) {
        return Counter.builder(RESULTS)
                .description("Processing results by outcome and failure reason")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(registry);
    }
}
