package dev.higor.trackflow.worker.audio;

import java.nio.file.Path;

/**
 * Turns the first audio stream of a file (audio or video) into what the worker and the browser
 * need. Video files work the same way: only the audio stream is read.
 */
public interface Transcoder {

    int PCM_SAMPLE_RATE = 22_050;

    /** Mono, {@value #PCM_SAMPLE_RATE} Hz, signed 16-bit little-endian, no header. */
    void decodeToPcm(Path source, Path target);

    /** MP3, 192 kbps constant bit rate, 44.1 kHz stereo. */
    void encodePreview(Path source, Path target);
}
