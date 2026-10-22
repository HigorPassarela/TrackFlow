package dev.higor.trackflow.api.version;

import java.util.Optional;
import java.util.UUID;

/** Public API of the version module for other modules. */
public interface TrackVersions {

    /** Confirmed versions only: pending uploads are invisible outside the version module. */
    Optional<VersionSummary> findSummary(UUID projectId, UUID versionId);

    /** Moves an uploaded version to processing and remembers which job is in charge of it. */
    void startProcessing(UUID versionId, UUID jobId);

    /**
     * Applies a successful result if the version is still processing <em>the same job</em>.
     * Duplicated or late results are ignored, so callers can deliver at least once.
     */
    void completeProcessing(UUID versionId, UUID jobId, ReadyMedia media);

    /** Same idempotency rule as {@link #completeProcessing}. */
    void failProcessing(UUID versionId, UUID jobId, String reason);
}
