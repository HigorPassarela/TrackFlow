package dev.higor.trackflow.worker.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;
import org.jspecify.annotations.Nullable;

/**
 * Reads the decoded PCM once, in chunks, and feeds every sample to the waveform and tempo
 * calculations. Nothing holds the whole track in memory.
 */
public final class PcmAnalyzer {

    private static final int CHUNK_BYTES = 64 * 1024;

    private PcmAnalyzer() {}

    /** @param pcm mono signed 16-bit little-endian, as written by {@link Transcoder#decodeToPcm} */
    public static PcmAnalysis analyze(Path pcm, int sampleRate) {
        PeaksCalculator peaks = new PeaksCalculator(sampleRate);
        BpmEstimator bpm = new BpmEstimator(sampleRate);
        long samples = 0;
        byte[] chunk = new byte[CHUNK_BYTES];
        try (InputStream input = new BufferedInputStream(Files.newInputStream(pcm))) {
            int read;
            int carry = 0;
            while ((read = input.readNBytes(chunk, carry, chunk.length - carry)) > 0) {
                int available = carry + read;
                int whole = available - available % 2;
                ByteBuffer buffer = ByteBuffer.wrap(chunk, 0, whole).order(ByteOrder.LITTLE_ENDIAN);
                while (buffer.hasRemaining()) {
                    float sample = buffer.getShort() / 32768f;
                    peaks.accept(sample);
                    bpm.accept(sample);
                    samples++;
                }
                // An odd read leaves half a sample, which belongs to the next chunk.
                carry = available - whole;
                if (carry > 0) {
                    chunk[0] = chunk[whole];
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read decoded audio " + pcm, e);
        }
        OptionalDouble tempo = bpm.estimate();
        return new PcmAnalysis(
                peaks.result(), tempo.isPresent() ? tempo.getAsDouble() : null, samples * 1000 / sampleRate);
    }

    /**
     * @param peaks normalized waveform, {@link PeaksCalculator#POINTS_PER_SECOND} per second
     * @param bpm estimated tempo, {@code null} when the track has no clear pulse
     * @param durationMs duration of the decoded audio stream
     */
    public record PcmAnalysis(List<Double> peaks, @Nullable Double bpm, long durationMs) {}
}
