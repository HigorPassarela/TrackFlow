package dev.higor.trackflow.contracts;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Worker → API: outcome of a {@link ProcessAudioJob}.
 *
 * <p>Sealed so consumers handle both outcomes with an exhaustive {@code switch}. The {@code outcome}
 * property in the JSON carries a logical name, never a Java class name.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "outcome")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AudioProcessed.Succeeded.class, name = "succeeded"),
    @JsonSubTypes.Type(value = AudioProcessed.Failed.class, name = "failed")
})
public sealed interface AudioProcessed {

    UUID messageId();

    UUID jobId();

    UUID versionId();

    Instant occurredAt();

    record Succeeded(
            UUID messageId,
            UUID jobId,
            UUID versionId,
            Instant occurredAt,
            String previewKey,
            String peaksKey,
            long durationMs,
            @Nullable Double bpmEstimated,
            double loudnessLufs,
            double truePeakDbtp)
            implements AudioProcessed {}

    /**
     * @param reason stable code shown to users, e.g. {@code unreadable-file}, {@code no-audio-stream},
     *     {@code duration-exceeded}, {@code processing-error}
     */
    record Failed(UUID messageId, UUID jobId, UUID versionId, Instant occurredAt, String reason)
            implements AudioProcessed {}
}
