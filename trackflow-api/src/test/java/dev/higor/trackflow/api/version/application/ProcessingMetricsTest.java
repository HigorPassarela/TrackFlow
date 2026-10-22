package dev.higor.trackflow.api.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.higor.trackflow.api.version.ReadyMedia;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import dev.higor.trackflow.api.version.domain.TrackVersionFixtures;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProcessingMetricsTest {

    /** Fixture versions are confirmed at CREATED + 60 s; results arrive two minutes later. */
    private static final Instant RESULT_AT = TrackVersionFixtures.CREATED.plusSeconds(180);

    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Mock
    private TrackVersionRepository versions;

    @Mock
    private ApplicationEventPublisher events;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private TrackVersionsService service;
    private TrackVersion version;

    @BeforeEach
    void setUp() {
        service = new TrackVersionsService(
                versions, events, new ProcessingMetrics(registry), Clock.fixed(RESULT_AT, ZoneOffset.UTC));
        version = TrackVersionFixtures.processing(JOB);
        given(versions.findByIdForUpdate(version.getId())).willReturn(Optional.of(version));
    }

    @Test
    void recordsSuccessAndTimeSinceConfirmation() {
        service.completeProcessing(
                version.getId(), JOB, new ReadyMedia("preview.mp3", "peaks.json", 1000L, 124.0, -9.4, 0.8));

        assertThat(registry.get(ProcessingMetrics.RESULTS)
                        .tag("outcome", "succeeded")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get(ProcessingMetrics.DURATION).timer().totalTime(TimeUnit.SECONDS))
                .isEqualTo(120);
    }

    @Test
    void countsFailuresByReason() {
        service.failProcessing(version.getId(), JOB, "no-audio-stream");

        assertThat(registry.get(ProcessingMetrics.RESULTS)
                        .tag("outcome", "failed")
                        .tag("reason", "no-audio-stream")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void doesNotCountDuplicatedResults() {
        var media = new ReadyMedia("preview.mp3", "peaks.json", 1000L, 124.0, -9.4, 0.8);

        service.completeProcessing(version.getId(), JOB, media);
        service.completeProcessing(version.getId(), JOB, media);

        assertThat(registry.get(ProcessingMetrics.RESULTS).counter().count()).isEqualTo(1);
        assertThat(registry.get(ProcessingMetrics.DURATION).timer().count()).isEqualTo(1);
    }
}
