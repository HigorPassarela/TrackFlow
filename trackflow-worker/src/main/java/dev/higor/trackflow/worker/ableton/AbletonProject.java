package dev.higor.trackflow.worker.ableton;

import dev.higor.trackflow.contracts.AbletonAnalyzed.Track;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What {@link AbletonProjectReader} extracts from a project, already in the shape of the contract.
 *
 * @param liveVersion the {@code Creator} attribute, e.g. {@code Ableton Live 12.4.6}
 * @param verifiedVersion whether the file comes from Live 11 or 12, the versions the reader was
 *     checked against
 * @param timeSignature as {@code 4/4}
 */
public record AbletonProject(
        @Nullable String liveVersion,
        boolean verifiedVersion,
        @Nullable Double tempo,
        @Nullable String timeSignature,
        List<Track> tracks) {

    public AbletonProject {
        tracks = List.copyOf(tracks);
    }
}
