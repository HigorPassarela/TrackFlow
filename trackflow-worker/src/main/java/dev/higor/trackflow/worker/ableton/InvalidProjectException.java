package dev.higor.trackflow.worker.ableton;

/** The {@code .als} itself cannot be analyzed; retrying cannot help. */
public class InvalidProjectException extends RuntimeException {

    public static final String INVALID_PROJECT_FILE = "invalid-project-file";
    public static final String PROJECT_FILE_TOO_LARGE = "project-file-too-large";

    private final String reason;

    public InvalidProjectException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** Stable code sent to the API in {@code AbletonAnalyzed.Failed}. */
    public String reason() {
        return reason;
    }
}
