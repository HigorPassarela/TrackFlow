package dev.higor.trackflow.api.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import dev.higor.trackflow.api.version.ReadyMedia;
import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.VersionStatusChanged;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import dev.higor.trackflow.api.version.domain.TrackVersionFixtures;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Results arrive at least once and possibly late; only the current job's first result counts. */
@ExtendWith(MockitoExtension.class)
class TrackVersionsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final UUID VERSION = TrackVersionFixtures.VERSION;
    private static final UUID FIRST_JOB = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SECOND_JOB = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final ReadyMedia FIRST = new ReadyMedia("preview.mp3", "peaks.json", 360_000L, 124.0, -9.4, 0.8);
    private static final ReadyMedia SECOND = new ReadyMedia("preview.mp3", "peaks.json", 1L, 90.0, -3.0, 2.0);

    @Mock
    private TrackVersionRepository versions;

    @Mock
    private ApplicationEventPublisher events;

    private TrackVersionsService service;

    @BeforeEach
    void setUp() {
        service = new TrackVersionsService(
                versions, events, new ProcessingMetrics(new SimpleMeterRegistry()), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Resultado duplicado: a versão fica com as métricas do primeiro")
    void ignoresDuplicatedSuccess() {
        TrackVersion version = TrackVersionFixtures.processing(FIRST_JOB);
        given(versions.findById(VERSION)).willReturn(Optional.of(version));

        service.completeProcessing(VERSION, FIRST_JOB, FIRST);
        service.completeProcessing(VERSION, FIRST_JOB, SECOND);

        assertThat(version.getStatus()).isEqualTo(VersionStatus.READY);
        assertThat(version.getLoudnessLufs()).isEqualTo(-9.4);
        assertThat(version.getDurationMs()).isEqualTo(360_000L);
        then(events)
                .should(times(1))
                .publishEvent(
                        new VersionStatusChanged(VERSION, TrackVersionFixtures.PROJECT, VersionStatus.READY, NOW));
    }

    @Test
    @DisplayName("Resultado de tentativa antiga: ignorado depois do reprocessamento")
    void ignoresResultOfAnAttemptThatWasReplaced() {
        TrackVersion version = TrackVersionFixtures.processing(FIRST_JOB);
        version.fail(FIRST_JOB, "processing-error", NOW);
        version.reprocess(NOW);
        given(versions.findById(VERSION)).willReturn(Optional.of(version));
        service.startProcessing(VERSION, SECOND_JOB);

        service.completeProcessing(VERSION, FIRST_JOB, FIRST);

        assertThat(version.getStatus()).isEqualTo(VersionStatus.PROCESSING);
        assertThat(version.getProcessingJobId()).isEqualTo(SECOND_JOB);
    }

    @Test
    void ignoresFailureArrivingAfterTheVersionIsReady() {
        TrackVersion version = TrackVersionFixtures.processing(FIRST_JOB);
        given(versions.findById(VERSION)).willReturn(Optional.of(version));
        service.completeProcessing(VERSION, FIRST_JOB, FIRST);

        service.failProcessing(VERSION, FIRST_JOB, "processing-error");

        assertThat(version.getStatus()).isEqualTo(VersionStatus.READY);
        assertThat(version.getFailureReason()).isNull();
    }

    @Test
    void appliesFailureOfTheCurrentJob() {
        TrackVersion version = TrackVersionFixtures.processing(FIRST_JOB);
        given(versions.findById(VERSION)).willReturn(Optional.of(version));

        service.failProcessing(VERSION, FIRST_JOB, "duration-exceeded");

        assertThat(version.getStatus()).isEqualTo(VersionStatus.FAILED);
        assertThat(version.getFailureReason()).isEqualTo("duration-exceeded");
    }

    @Test
    void findsOnlyConfirmedVersionsForOtherModules() {
        TrackVersion pending = TrackVersionFixtures.pendingWav();
        given(versions.findByIdAndProjectId(VERSION, TrackVersionFixtures.PROJECT))
                .willReturn(Optional.of(pending));

        assertThat(service.findSummary(TrackVersionFixtures.PROJECT, VERSION)).isEmpty();
    }
}
