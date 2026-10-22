package dev.higor.trackflow.worker.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.higor.trackflow.worker.audio.LoudnessMeter.Loudness;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("ffmpeg")
class FfmpegLoudnessMeterTest {

    private final LoudnessMeter meter = FfmpegTestSupport.loudnessMeter();

    @TempDir
    Path directory;

    /**
     * EBU R 128 calibrates K-weighting so that a 1 kHz sine reads its RMS level: amplitude 0.5 is
     * -6.0 dBFS peak and -9.0 dB RMS, so -9.0 LUFS.
     */
    @Test
    void measuresReferenceSineWithinHalfALoudnessUnit() {
        Path sine = TestSignals.writeWav(directory.resolve("sine.wav"), TestSignals.sine(1000, 0.5, 10));

        Loudness loudness = meter.measure(sine);

        assertThat(loudness.integratedLufs()).isCloseTo(-9.0, within(0.5));
        assertThat(loudness.truePeakDbtp()).isCloseTo(-6.0, within(0.5));
    }

    @Test
    void roundsToOneDecimalPlace() {
        Path sine = TestSignals.writeWav(directory.resolve("sine.wav"), TestSignals.sine(1000, 0.3, 5));

        Loudness loudness = meter.measure(sine);

        assertThat(loudness.integratedLufs() * 10).isEqualTo(Math.rint(loudness.integratedLufs() * 10));
        assertThat(loudness.truePeakDbtp() * 10).isEqualTo(Math.rint(loudness.truePeakDbtp() * 10));
    }

    @Test
    void reportsSilenceWithAFiniteFloor() {
        Path silence = TestSignals.writeWav(directory.resolve("silence.wav"), new float[TestSignals.SAMPLE_RATE * 3]);

        Loudness loudness = meter.measure(silence);

        assertThat(loudness.truePeakDbtp()).isEqualTo(-144.0);
        assertThat(loudness.integratedLufs()).isLessThanOrEqualTo(-70.0);
    }
}
