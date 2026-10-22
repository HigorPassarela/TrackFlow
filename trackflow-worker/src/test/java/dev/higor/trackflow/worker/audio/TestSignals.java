package dev.higor.trackflow.worker.audio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Audio generated inside the tests instead of versioned WAV files: the expected values (tempo,
 * peak, loudness) are then known exactly.
 */
public final class TestSignals {

    public static final int SAMPLE_RATE = Transcoder.PCM_SAMPLE_RATE;

    private TestSignals() {}

    public static float[] sine(double frequencyHz, double amplitude, double seconds) {
        float[] samples = new float[(int) (seconds * SAMPLE_RATE)];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) (amplitude * Math.sin(2 * Math.PI * frequencyHz * i / SAMPLE_RATE));
        }
        return samples;
    }

    /** A 10 ms decaying 1 kHz click on every beat, silence in between. */
    public static float[] metronome(double bpm, double seconds) {
        float[] samples = new float[(int) (seconds * SAMPLE_RATE)];
        int clickLength = SAMPLE_RATE / 100;
        double beatSamples = 60.0 * SAMPLE_RATE / bpm;
        for (double beat = 0; beat < samples.length; beat += beatSamples) {
            int start = (int) Math.round(beat);
            for (int i = 0; i < clickLength && start + i < samples.length; i++) {
                double decay = 1 - (double) i / clickLength;
                samples[start + i] = (float) (0.8 * decay * Math.sin(2 * Math.PI * 1000 * i / SAMPLE_RATE));
            }
        }
        return samples;
    }

    /** A sustained chord with a slow fade-in: sound all the time, but no transients. */
    public static float[] pad(double seconds) {
        float[] samples = new float[(int) (seconds * SAMPLE_RATE)];
        int fadeIn = SAMPLE_RATE * 4;
        for (int i = 0; i < samples.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double chord = Math.sin(2 * Math.PI * 220 * t)
                    + Math.sin(2 * Math.PI * 277.18 * t)
                    + Math.sin(2 * Math.PI * 329.63 * t);
            samples[i] = (float) (0.2 * chord * Math.min(1, (double) i / fadeIn));
        }
        return samples;
    }

    public static float[] concat(float[] first, float[] second) {
        float[] joined = new float[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    /** Mono 16-bit PCM WAV at {@link #SAMPLE_RATE}. */
    public static Path writeWav(Path target, float[] samples) {
        int dataBytes = samples.length * 2;
        ByteBuffer wav = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes()).putInt(36 + dataBytes).put("WAVE".getBytes());
        wav.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1);
        wav.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort((short) 2).putShort((short) 16);
        wav.put("data".getBytes()).putInt(dataBytes);
        for (float sample : samples) {
            wav.putShort((short) Math.round(Math.max(-1, Math.min(1, sample)) * 32767));
        }
        try {
            return Files.write(target, wav.array());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
