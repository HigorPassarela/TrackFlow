package dev.higor.trackflow.api.ableton;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Public API of the ableton module, used by {@code processing}. Results follow the same idempotency
 * rule as versions: they only apply to the pending analysis with the same job id.
 */
public interface AbletonAnalyses {

    /**
     * Records that an analysis job is about to be sent for the version's project file. Commits on
     * its own, before the job leaves, so even a result that comes back at once finds the analysis.
     * Registering again while the analysis is pending (a resubmitted confirmation) hands it to the
     * new job; results of the old one are then ignored.
     */
    void registerPending(UUID versionId, UUID projectId, UUID jobId);

    void complete(UUID versionId, UUID jobId, ProjectSnapshot snapshot);

    void fail(UUID versionId, UUID jobId, String reason);

    /** What the worker read from the {@code .als}. */
    record ProjectSnapshot(
            @Nullable String liveVersion,
            boolean verifiedVersion,
            @Nullable Double tempo,
            @Nullable String timeSignature,
            List<Track> tracks) {

        public ProjectSnapshot {
            tracks = List.copyOf(tracks);
        }
    }

    /** {@code id} is Ableton's own track id, stable across renames. */
    record Track(String id, String type, String name, List<String> devices, List<String> plugins) {

        public Track {
            devices = List.copyOf(devices);
            plugins = List.copyOf(plugins);
        }
    }
}
