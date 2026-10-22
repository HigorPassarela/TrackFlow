package dev.higor.trackflow.worker.audio;

import static dev.higor.trackflow.worker.audio.TestSignals.SAMPLE_RATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeaksCalculatorTest {

    @Test
    @DisplayName("Scenario: track starting with 2 seconds of absolute silence")
    void silenceIsZeroAndTheLoudestPointIsOne() {
        float[] signal = TestSignals.concat(new float[2 * SAMPLE_RATE], TestSignals.sine(440, 0.4, 3));

        List<Double> peaks = peaksOf(signal);

        assertThat(peaks.subList(0, 2 * PeaksCalculator.POINTS_PER_SECOND)).containsOnly(0.0);
        assertThat(peaks).contains(1.0).allSatisfy(point -> assertThat(point).isBetween(0.0, 1.0));
    }

    @Test
    void producesThirtyPointsPerSecond() {
        List<Double> peaks = peaksOf(TestSignals.sine(440, 0.5, 4));

        assertThat(peaks).hasSize(4 * PeaksCalculator.POINTS_PER_SECOND);
    }

    @Test
    void keepsRelativeLevelsWithThreeDecimals() {
        float[] signal = TestSignals.concat(TestSignals.sine(440, 0.8, 1), TestSignals.sine(440, 0.2, 1));

        List<Double> peaks = peaksOf(signal);

        assertThat(peaks.getLast()).isCloseTo(0.25, offset(0.002));
        assertThat(peaks).allSatisfy(point -> assertThat(point * 1000).isEqualTo(Math.rint(point * 1000)));
    }

    @Test
    void silentTrackIsAllZeros() {
        assertThat(peaksOf(new float[SAMPLE_RATE])).isNotEmpty().containsOnly(0.0);
    }

    private static List<Double> peaksOf(float[] signal) {
        PeaksCalculator calculator = new PeaksCalculator(SAMPLE_RATE);
        for (float sample : signal) {
            calculator.accept(sample);
        }
        return calculator.result();
    }
}
