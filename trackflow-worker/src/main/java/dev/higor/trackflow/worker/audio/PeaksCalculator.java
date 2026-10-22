package dev.higor.trackflow.worker.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Waveform data for the player: the maximum absolute amplitude of each window, {@value
 * #POINTS_PER_SECOND} windows per second, normalized so the loudest point of the track is 1 (design
 * D7). Silence stays exactly 0.
 *
 * <p>Fed sample by sample so a 20-minute track never has to fit in memory at once.
 */
public final class PeaksCalculator {

    public static final int POINTS_PER_SECOND = 30;

    private final int samplesPerPoint;
    private final List<Float> points = new ArrayList<>();
    private float windowMax;
    private int windowSamples;

    public PeaksCalculator(int sampleRate) {
        this.samplesPerPoint = Math.max(1, Math.round((float) sampleRate / POINTS_PER_SECOND));
    }

    /** @param sample in [-1, 1] */
    public void accept(float sample) {
        windowMax = Math.max(windowMax, Math.abs(sample));
        if (++windowSamples == samplesPerPoint) {
            closeWindow();
        }
    }

    /** Normalized points rounded to 3 decimal places. Empty for an empty stream. */
    public List<Double> result() {
        if (windowSamples > 0) {
            closeWindow();
        }
        float loudest = 0;
        for (float point : points) {
            loudest = Math.max(loudest, point);
        }
        if (loudest == 0) {
            return points.stream().map(point -> 0.0).toList();
        }
        float scale = loudest;
        return points.stream()
                .map(point -> Math.round(point / scale * 1000) / 1000.0)
                .toList();
    }

    private void closeWindow() {
        points.add(windowMax);
        windowMax = 0;
        windowSamples = 0;
    }
}
