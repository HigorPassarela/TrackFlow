package dev.higor.trackflow.api.version.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RequestUpload(
        UUID projectId,
        UUID userId,
        String fileName,
        String contentType,
        long sizeBytes,
        @Nullable AbletonAttachment ableton,
        @Nullable String title,
        @Nullable String notes) {

    /** The optional Ableton Live project uploaded together with the audio. */
    public record AbletonAttachment(String fileName, long sizeBytes) {}
}
