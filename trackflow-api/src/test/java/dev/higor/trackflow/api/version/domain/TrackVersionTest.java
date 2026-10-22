package dev.higor.trackflow.api.version.domain;

import static dev.higor.trackflow.api.version.domain.TrackVersionFixtures.CREATED;
import static dev.higor.trackflow.api.version.domain.TrackVersionFixtures.MEDIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.version.ReadyMedia;
import dev.higor.trackflow.api.version.VersionStatus;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TrackVersionTest {

    private static final Instant LATER = CREATED.plusSeconds(3600);
    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_JOB = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @Nested
    class ValidTransitions {

        @Test
        void confirmingAssignsTheNumberAndMarksUploaded() {
            TrackVersion version = TrackVersionFixtures.pendingWav();

            version.confirm(4, LATER);

            assertThat(version.getStatus()).isEqualTo(VersionStatus.UPLOADED);
            assertThat(version.getNumber()).isEqualTo(4);
            assertThat(version.getConfirmedAt()).isEqualTo(LATER);
        }

        @Test
        void startingProcessingRemembersTheJob() {
            TrackVersion version = TrackVersionFixtures.uploaded(1);

            version.startProcessing(JOB, LATER);

            assertThat(version.getStatus()).isEqualTo(VersionStatus.PROCESSING);
            assertThat(version.getProcessingJobId()).isEqualTo(JOB);
        }

        @Test
        void completingStoresTheMediaAndMetrics() {
            TrackVersion version = TrackVersionFixtures.processing(JOB);

            boolean applied = version.complete(JOB, MEDIA, LATER);

            assertThat(applied).isTrue();
            assertThat(version.getStatus()).isEqualTo(VersionStatus.READY);
            assertThat(version.getDurationMs()).isEqualTo(360_000L);
            assertThat(version.getBpmEstimated()).isEqualTo(124.0);
            assertThat(version.getTruePeakDbtp()).isEqualTo(0.8);
            assertThat(version.getPreviewKey()).isEqualTo(MEDIA.previewKey());
        }

        @Test
        void failingKeepsTheReason() {
            TrackVersion version = TrackVersionFixtures.processing(JOB);

            boolean applied = version.fail(JOB, "unreadable-file", LATER);

            assertThat(applied).isTrue();
            assertThat(version.getStatus()).isEqualTo(VersionStatus.FAILED);
            assertThat(version.getFailureReason()).isEqualTo("unreadable-file");
        }

        @Test
        void reprocessingSendsAFailedVersionBackToUploadedWithoutJob() {
            TrackVersion version = TrackVersionFixtures.failed();

            version.reprocess(LATER);

            assertThat(version.getStatus()).isEqualTo(VersionStatus.UPLOADED);
            assertThat(version.getProcessingJobId()).isNull();
        }

        @Test
        void reprocessedVersionAcceptsANewJob() {
            TrackVersion version = TrackVersionFixtures.failed();
            version.reprocess(LATER);

            version.startProcessing(OTHER_JOB, LATER);

            assertThat(version.getProcessingJobId()).isEqualTo(OTHER_JOB);
        }
    }

    @Nested
    class InvalidTransitions {

        static Stream<Arguments> invalidTransitions() {
            Consumer<TrackVersion> confirm = version -> version.confirm(9, LATER);
            Consumer<TrackVersion> start = version -> version.startProcessing(JOB, LATER);
            Consumer<TrackVersion> reprocess = version -> version.reprocess(LATER);
            Supplier<TrackVersion> pending = TrackVersionFixtures::pendingWav;
            Supplier<TrackVersion> uploaded = () -> TrackVersionFixtures.uploaded(1);
            Supplier<TrackVersion> processing = () -> TrackVersionFixtures.processing(JOB);
            Supplier<TrackVersion> ready = TrackVersionFixtures::ready;
            Supplier<TrackVersion> failed = TrackVersionFixtures::failed;
            return Stream.of(
                    Arguments.of("confirm UPLOADED", uploaded, confirm, "version-already-confirmed"),
                    Arguments.of("confirm PROCESSING", processing, confirm, "version-already-confirmed"),
                    Arguments.of("confirm READY", ready, confirm, "version-already-confirmed"),
                    Arguments.of("confirm FAILED", failed, confirm, "version-already-confirmed"),
                    Arguments.of("start PENDING_UPLOAD", pending, start, "version-not-uploaded"),
                    Arguments.of("start PROCESSING", processing, start, "version-not-uploaded"),
                    Arguments.of("start READY", ready, start, "version-not-uploaded"),
                    Arguments.of("start FAILED", failed, start, "version-not-uploaded"),
                    Arguments.of("reprocess PENDING_UPLOAD", pending, reprocess, "version-not-failed"),
                    Arguments.of("reprocess UPLOADED", uploaded, reprocess, "version-not-failed"),
                    Arguments.of("reprocess PROCESSING", processing, reprocess, "version-not-failed"),
                    Arguments.of("reprocess READY", ready, reprocess, "version-not-failed"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidTransitions")
        void throwsBusinessRuleException(
                String name, Supplier<TrackVersion> state, Consumer<TrackVersion> transition, String code) {
            TrackVersion version = state.get();
            VersionStatus before = version.getStatus();

            assertThatThrownBy(() -> transition.accept(version))
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            error -> assertThat(error.code()).isEqualTo(code));
            assertThat(version.getStatus()).isEqualTo(before);
        }

        @Test
        void requireReadyRejectsAnyOtherState() {
            assertThatThrownBy(() -> TrackVersionFixtures.processing(JOB).requireReady())
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            error -> assertThat(error.code()).isEqualTo("version-not-ready"));
        }
    }

    @Nested
    class IgnoredResults {

        @Test
        void ignoresResultOfAnOlderJob() {
            TrackVersion version = TrackVersionFixtures.processing(JOB);

            assertThat(version.complete(OTHER_JOB, MEDIA, LATER)).isFalse();
            assertThat(version.fail(OTHER_JOB, "processing-error", LATER)).isFalse();
            assertThat(version.getStatus()).isEqualTo(VersionStatus.PROCESSING);
        }

        @Test
        void ignoresDuplicatedSuccessKeepingTheFirstMetrics() {
            TrackVersion version = TrackVersionFixtures.processing(JOB);
            version.complete(JOB, MEDIA, LATER);

            boolean applied =
                    version.complete(JOB, new ReadyMediaBuilder().loudness(-3.0).build(), LATER.plusSeconds(1));

            assertThat(applied).isFalse();
            assertThat(version.getLoudnessLufs()).isEqualTo(-9.4);
        }

        @Test
        void ignoresFailureArrivingAfterSuccess() {
            TrackVersion version = TrackVersionFixtures.processing(JOB);
            version.complete(JOB, MEDIA, LATER);

            assertThat(version.fail(JOB, "processing-error", LATER)).isFalse();
            assertThat(version.getStatus()).isEqualTo(VersionStatus.READY);
        }
    }

    @Nested
    class Details {

        @Test
        void informedBpmWinsOverTheEstimateAndBothStayVisible() {
            TrackVersion version = TrackVersionFixtures.processing(JOB);
            version.complete(JOB, new ReadyMediaBuilder().bpm(63.0).build(), LATER);

            version.correctBpm(126.0, LATER);

            assertThat(version.effectiveBpm()).isEqualTo(126.0);
            assertThat(version.getBpmEstimated()).isEqualTo(63.0);
        }

        @Test
        void rejectsBpmOutsideTheAcceptedRange() {
            TrackVersion version = TrackVersionFixtures.ready();

            assertThatThrownBy(() -> version.correctBpm(251.0, LATER)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void storageKeysAreAssignedOnlyOnce() {
            TrackVersion version = TrackVersionFixtures.pendingWav();

            assertThatThrownBy(() -> version.assignStorageKeys("other", null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    /** Small helper to vary one metric of {@link TrackVersionFixtures#MEDIA}. */
    private static final class ReadyMediaBuilder {

        private Double bpm = MEDIA.bpmEstimated();
        private double loudness = MEDIA.loudnessLufs();

        ReadyMediaBuilder bpm(Double value) {
            bpm = value;
            return this;
        }

        ReadyMediaBuilder loudness(double value) {
            loudness = value;
            return this;
        }

        ReadyMedia build() {
            return new ReadyMedia(
                    MEDIA.previewKey(), MEDIA.peaksKey(), MEDIA.durationMs(), bpm, loudness, MEDIA.truePeakDbtp());
        }
    }
}
