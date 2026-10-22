package dev.higor.trackflow.worker.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("ffmpeg")
class FfprobeMediaProbeTest {

    private static final Duration LIMIT = FfmpegTestSupport.PROPERTIES.maxDuration();

    private final MediaProbe probe = FfmpegTestSupport.probe();

    @TempDir
    Path directory;

    @Test
    void readsAudioAndDurationOfAValidWav() {
        Path wav = TestSignals.writeWav(directory.resolve("bounce.wav"), TestSignals.sine(440, 0.5, 3));

        MediaInfo info = probe.probe(wav).requireProcessable(LIMIT);

        assertThat(info.hasAudio()).isTrue();
        assertThat(info.durationMs()).isEqualTo(3000);
    }

    @Test
    @DisplayName("Scenario: corrupted file")
    void rejectsFileThatIsNotMediaAsUnreadable() throws Exception {
        byte[] garbage = new byte[64 * 1024];
        new Random(42).nextBytes(garbage);
        Path fake = Files.write(directory.resolve("bounce.wav"), garbage);

        assertThatThrownBy(() -> probe.probe(fake))
                .isInstanceOfSatisfying(
                        InvalidMediaException.class,
                        error -> assertThat(error.reason()).isEqualTo(InvalidMediaException.UNREADABLE_FILE));
    }

    @Test
    @DisplayName("Scenario: video without audio")
    void rejectsVideoWithoutAudioStream() {
        Path video = FfmpegTestFiles.videoWithoutAudio(directory.resolve("screen.mp4"));

        MediaInfo info = probe.probe(video);

        assertThat(info.hasAudio()).isFalse();
        assertThatThrownBy(() -> info.requireProcessable(LIMIT))
                .isInstanceOfSatisfying(
                        InvalidMediaException.class,
                        error -> assertThat(error.reason()).isEqualTo(InvalidMediaException.NO_AUDIO_STREAM));
    }

    @Test
    @DisplayName("Scenario: file too long")
    void rejectsTrackLongerThanTwentyMinutes() {
        // 45 minutes of silence compresses to a few kilobytes as FLAC.
        Path long45 = FfmpegTestFiles.generate(
                directory.resolve("long.flac"), "-f", "lavfi", "-i", "anullsrc=r=8000:cl=mono", "-t", "2700");

        MediaInfo info = probe.probe(long45);

        assertThat(info.durationMs()).isEqualTo(2_700_000);
        assertThatThrownBy(() -> info.requireProcessable(LIMIT))
                .isInstanceOfSatisfying(
                        InvalidMediaException.class,
                        error -> assertThat(error.reason()).isEqualTo(InvalidMediaException.DURATION_EXCEEDED));
    }
}
