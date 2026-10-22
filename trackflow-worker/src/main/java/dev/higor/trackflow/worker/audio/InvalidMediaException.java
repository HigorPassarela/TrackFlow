package dev.higor.trackflow.worker.audio;

/**
 * The uploaded file itself is the problem: retrying cannot help, so the version fails right away
 * (design D7). Anything else is treated as temporary and retried.
 */
public class InvalidMediaException extends RuntimeException {

    public static final String UNREADABLE_FILE = "unreadable-file";
    public static final String NO_AUDIO_STREAM = "no-audio-stream";
    public static final String DURATION_EXCEEDED = "duration-exceeded";

    private final String reason;

    public InvalidMediaException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** Stable code sent to the API in {@code AudioProcessed.Failed}. */
    public String reason() {
        return reason;
    }
}
