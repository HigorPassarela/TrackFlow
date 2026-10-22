package dev.higor.trackflow.worker.audio;

import java.util.Arrays;
import java.util.OptionalDouble;

/**
 * Tempo estimate in pure Java (design D7). Always shown to users as an estimate.
 *
 * <ol>
 *   <li><b>Onset envelope.</b> Energy of windows of {@value #WINDOW} samples every {@value #HOP}
 *       samples. The envelope is how much the log energy rose from one window to the next; drops,
 *       and rises smaller than {@value #MIN_RISE_DB} dB, count as zero. A pad without transients
 *       therefore has a flat envelope and no tempo. The envelope is then lightly smoothed.
 *   <li><b>Autocorrelation</b> of the envelope for the periods between {@value #MIN_BPM} and
 *       {@value #MAX_BPM} BPM, normalized so that a period which repeats perfectly scores 1.
 *   <li><b>Pick and fold.</b> The strongest period wins. A result below 80 BPM is doubled, and one
 *       above 160 halved, when that harmonic has at least 80% of the strength: a kick on every
 *       beat also repeats every two beats.
 *   <li><b>Refine.</b> One window lasts about 23 ms, too coarse for one decimal place. The period is
 *       refined with the autocorrelation peaks at its first {@value #REFINE_MULTIPLES} multiples,
 *       each located between windows by parabolic interpolation.
 * </ol>
 *
 * When the strongest period scores below {@value #MIN_STRENGTH}, there is no clear pulse and no
 * tempo is reported, rather than an invented one.
 */
public final class BpmEstimator {

    static final int WINDOW = 1024;
    static final int HOP = 512;
    static final double MIN_BPM = 60;
    static final double MAX_BPM = 200;
    static final double MIN_STRENGTH = 0.3;
    static final double MIN_RISE_DB = 0.5;
    static final int REFINE_MULTIPLES = 8;

    private static final double FOLD_LOW_BPM = 80;
    private static final double FOLD_HIGH_BPM = 160;
    private static final double HARMONIC_RATIO = 0.8;
    private static final double MIN_LOG_RISE = MIN_RISE_DB / 10 * Math.log(10);
    private static final double SILENCE_ENERGY = 1e-10;
    private static final double[] SMOOTHING = {1 / 9.0, 2 / 9.0, 3 / 9.0, 2 / 9.0, 1 / 9.0};

    private final double framesPerSecond;
    private final float[] window = new float[WINDOW];
    private int filled;
    private double[] envelope = new double[1024];
    private int frames;
    private double previousLogEnergy = Math.log(SILENCE_ENERGY);

    public BpmEstimator(int sampleRate) {
        this.framesPerSecond = (double) sampleRate / HOP;
    }

    /** @param sample in [-1, 1] */
    public void accept(float sample) {
        window[filled++] = sample;
        if (filled == WINDOW) {
            addFrame();
            // Keep the second half: windows overlap by HOP samples.
            System.arraycopy(window, HOP, window, 0, WINDOW - HOP);
            filled = WINDOW - HOP;
        }
    }

    /** BPM rounded to one decimal place, or empty when the track has no clear pulse. */
    public OptionalDouble estimate() {
        int minLag = (int) Math.floor(lagOf(MAX_BPM));
        int maxLag = (int) Math.ceil(lagOf(MIN_BPM));
        int longestLag = Math.min(frames - 1, (maxLag + 2) * REFINE_MULTIPLES);
        if (longestLag <= maxLag + 1) {
            return OptionalDouble.empty();
        }
        double[] correlation = autocorrelation(smoothed(Arrays.copyOf(envelope, frames)), longestLag);
        if (correlation.length == 0) {
            return OptionalDouble.empty();
        }

        int best = minLag;
        for (int lag = minLag; lag <= maxLag; lag++) {
            if (correlation[lag] > correlation[best]) {
                best = lag;
            }
        }
        if (correlation[best] < MIN_STRENGTH) {
            return OptionalDouble.empty();
        }

        double period = fold(best, correlation);
        double bpm = 60 * framesPerSecond / refine(period, correlation);
        if (bpm < MIN_BPM || bpm > MAX_BPM) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.round(bpm * 10) / 10.0);
    }

    private void addFrame() {
        double energy = 0;
        for (float sample : window) {
            energy += sample * sample;
        }
        double logEnergy = Math.log(energy + SILENCE_ENERGY);
        double rise = logEnergy - previousLogEnergy;
        previousLogEnergy = logEnergy;
        if (frames == envelope.length) {
            envelope = Arrays.copyOf(envelope, frames * 2);
        }
        envelope[frames++] = rise > MIN_LOG_RISE ? rise : 0;
    }

    /** A doubled or halved period when that harmonic is strong enough and closer to 80-160 BPM. */
    private double fold(int lag, double[] correlation) {
        double bpm = 60 * framesPerSecond / lag;
        if (bpm < FOLD_LOW_BPM) {
            int half = peakNear(lag / 2.0, correlation);
            if (correlation[half] >= HARMONIC_RATIO * correlation[lag]) {
                return lag / 2.0;
            }
        } else if (bpm > FOLD_HIGH_BPM) {
            int twice = peakNear(lag * 2.0, correlation);
            if (twice < correlation.length && correlation[twice] >= HARMONIC_RATIO * correlation[lag]) {
                return lag * 2.0;
            }
        }
        return lag;
    }

    /**
     * Least-squares period from the interpolated peaks at {@code k * period}, k = 1..n. The estimate
     * is updated after each multiple, so the error of the first guess does not grow with k and push
     * the search onto the wrong peak.
     */
    private static double refine(double period, double[] correlation) {
        double estimate = period;
        double weighted = 0;
        double squares = 0;
        for (int k = 1; k <= REFINE_MULTIPLES; k++) {
            double expected = k * estimate;
            if (Math.round(expected) + 2 >= correlation.length) {
                break;
            }
            double peak = interpolatedPeak(peakNear(expected, correlation), correlation);
            weighted += k * peak;
            squares += (double) k * k;
            estimate = weighted / squares;
        }
        return estimate;
    }

    /** The highest point within two windows of {@code expected}. */
    private static int peakNear(double expected, double[] correlation) {
        int center = (int) Math.round(expected);
        int best = Math.max(1, center - 2);
        for (int lag = best; lag <= center + 2 && lag < correlation.length; lag++) {
            if (correlation[lag] > correlation[best]) {
                best = lag;
            }
        }
        return best;
    }

    /** Vertex of the parabola through the peak and its two neighbours. */
    private static double interpolatedPeak(int lag, double[] correlation) {
        if (lag < 1 || lag + 1 >= correlation.length) {
            return lag;
        }
        double left = correlation[lag - 1];
        double center = correlation[lag];
        double right = correlation[lag + 1];
        double curvature = left - 2 * center + right;
        if (curvature >= 0) {
            return lag;
        }
        return lag + 0.5 * (left - right) / curvature;
    }

    /**
     * Spreads each onset over its neighbouring windows. A beat rarely lasts a whole number of
     * windows, so without this the correlation of one period splits between two lags and loses to
     * a multiple that happens to land on a whole number.
     */
    private static double[] smoothed(double[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            double sum = 0;
            for (int offset = -2; offset <= 2; offset++) {
                int index = i + offset;
                if (index >= 0 && index < values.length) {
                    sum += SMOOTHING[offset + 2] * values[index];
                }
            }
            result[i] = sum;
        }
        return result;
    }

    /** Normalized by the zero-lag value; empty when the envelope is flat. */
    private static double[] autocorrelation(double[] values, int maxLag) {
        double mean = Arrays.stream(values).average().orElse(0);
        double[] centered = Arrays.stream(values).map(value -> value - mean).toArray();
        double variance = 0;
        for (double value : centered) {
            variance += value * value;
        }
        if (variance < 1e-9) {
            return new double[0];
        }
        double[] correlation = new double[maxLag + 1];
        for (int lag = 0; lag <= maxLag; lag++) {
            double sum = 0;
            for (int i = 0; i + lag < centered.length; i++) {
                sum += centered[i] * centered[i + lag];
            }
            correlation[lag] = sum / variance;
        }
        return correlation;
    }

    private double lagOf(double bpm) {
        return 60 * framesPerSecond / bpm;
    }
}
