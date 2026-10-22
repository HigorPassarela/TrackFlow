package dev.higor.trackflow.worker.audio;

import java.time.Duration;

/** What the worker needs to know about an uploaded file before decoding it. */
public record MediaInfo(boolean hasAudio, long durationMs) {

    /** Rejects files the spec says cannot become a version: without audio or too long. */
    public MediaInfo requireProcessable(Duration maxDuration) {
        if (!hasAudio) {
            throw new InvalidMediaException(InvalidMediaException.NO_AUDIO_STREAM, "No audio stream found");
        }
        if (durationMs > maxDuration.toMillis()) {
            throw new InvalidMediaException(
                    InvalidMediaException.DURATION_EXCEEDED,
                    "Duration of %d ms is above the limit of %s".formatted(durationMs, maxDuration));
        }
        return this;
    }
}
