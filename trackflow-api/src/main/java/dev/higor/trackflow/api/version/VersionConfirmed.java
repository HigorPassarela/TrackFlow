package dev.higor.trackflow.api.version;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published in the same transaction that assigns the version number. The Modulith event registry
 * stores it with that transaction, so listeners get it even if the app dies right after commit.
 */
public record VersionConfirmed(
        UUID versionId,
        UUID projectId,
        int number,
        String sourceKey,
        @Nullable String abletonKey) {}
