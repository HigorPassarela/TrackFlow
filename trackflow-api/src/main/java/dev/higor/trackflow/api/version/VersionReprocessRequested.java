package dev.higor.trackflow.api.version;

import java.util.UUID;

/**
 * A failed version was sent back to processing. Separate from {@link VersionConfirmed} because a
 * reprocess must not register a new Ableton analysis or count as new activity.
 */
public record VersionReprocessRequested(UUID versionId, UUID projectId, String sourceKey) {}
