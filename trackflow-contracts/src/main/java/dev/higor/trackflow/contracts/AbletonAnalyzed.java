package dev.higor.trackflow.contracts;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Worker → API: outcome of an {@link AnalyzeAbletonProjectJob}. Same shape rules as {@link
 * AudioProcessed}: a sealed type whose JSON carries a logical {@code outcome} name.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "outcome")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AbletonAnalyzed.Succeeded.class, name = "succeeded"),
    @JsonSubTypes.Type(value = AbletonAnalyzed.Failed.class, name = "failed")
})
public sealed interface AbletonAnalyzed {

    UUID messageId();

    UUID jobId();

    UUID versionId();

    Instant occurredAt();

    /**
     * @param liveVersion the {@code Creator} attribute, e.g. {@code Ableton Live 12.1.5}
     * @param verifiedVersion whether the file comes from a Live version the reader was checked against
     * @param timeSignature as {@code 4/4}
     */
    record Succeeded(
            UUID messageId,
            UUID jobId,
            UUID versionId,
            Instant occurredAt,
            @Nullable String liveVersion,
            boolean verifiedVersion,
            @Nullable Double tempo,
            @Nullable String timeSignature,
            List<Track> tracks)
            implements AbletonAnalyzed {

        public Succeeded {
            tracks = List.copyOf(tracks);
        }
    }

    /**
     * @param reason stable code shown to members: {@code invalid-project-file} or {@code
     *     project-file-too-large}
     */
    record Failed(UUID messageId, UUID jobId, UUID versionId, Instant occurredAt, String reason)
            implements AbletonAnalyzed {}

    /**
     * One track of the project. {@code id} is Ableton's own track id, stable across saves, so a
     * rename keeps it and the diff can tell a rename from a replacement.
     *
     * @param type {@code audio}, {@code midi}, {@code group} or {@code return}
     */
    record Track(String id, String type, String name, List<String> devices, List<String> plugins) {

        public Track {
            devices = List.copyOf(devices);
            plugins = List.copyOf(plugins);
        }
    }
}
