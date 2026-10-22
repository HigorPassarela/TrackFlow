package dev.higor.trackflow.api.version;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What other modules may know about a confirmed version.
 *
 * @param durationMs present once the version is {@link VersionStatus#READY}
 */
public record VersionSummary(
        UUID id,
        UUID projectId,
        int number,
        VersionStatus status,
        @Nullable Long durationMs) {

    public boolean isReady() {
        return status == VersionStatus.READY;
    }
}
