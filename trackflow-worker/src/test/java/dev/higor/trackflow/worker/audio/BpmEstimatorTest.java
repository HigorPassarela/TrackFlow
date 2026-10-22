package dev.higor.trackflow.worker.audio;

import static dev.higor.trackflow.worker.audio.TestSignals.SAMPLE_RATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BpmEstimatorTest {

    @ParameterizedTest(name = "{0} BPM")
    @ValueSource(doubles = {124, 128})
    @DisplayName("Scenario: 2-minute synthetic metronome")
    void estimatesMetronomeWithinOneBpm(double bpm) {
        OptionalDouble estimate = estimate(TestSignals.metronome(bpm, 120));

        assertThat(estimate).isPresent();
        assertThat(estimate.getAsDouble()).isBetween(bpm - 1, bpm + 1);
    }

    @Test
    void foldsSlowTempoIntoTheUsualRange() {
        OptionalDouble estimate = estimate(TestSignals.metronome(140, 60));

        assertThat(estimate).isPresent();
        assertThat(estimate.getAsDouble()).isBetween(139.0, 141.0);
    }

    @Test
    void reportsOneDecimalPlace() {
        double estimate = estimate(TestSignals.metronome(124, 60)).orElseThrow();

        assertThat(estimate * 10).isEqualTo(Math.rint(estimate * 10));
    }

    @Test
    @DisplayName("Scenario: continuous pad without transients")
    void reportsNothingForTrackWithoutPulse() {
        assertThat(estimate(TestSignals.pad(60))).isEmpty();
    }

    @Test
    void reportsNothingForSilence() {
        assertThat(estimate(new float[30 * SAMPLE_RATE])).isEmpty();
    }

    @Test
    void reportsNothingForTrackTooShortToShowAPulse() {
        assertThat(estimate(TestSignals.metronome(124, 1))).isEmpty();
    }

    private static OptionalDouble estimate(float[] signal) {
        BpmEstimator estimator = new BpmEstimator(SAMPLE_RATE);
        for (float sample : signal) {
            estimator.accept(sample);
        }
        return estimator.estimate();
    }
}
