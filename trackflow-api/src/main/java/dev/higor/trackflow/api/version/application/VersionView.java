package dev.higor.trackflow.api.version.application;

import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record VersionView(
        UUID id,
        UUID projectId,
        @Nullable Integer number,
        VersionStatus status,
        @Nullable String title,
        @Nullable String notes,
        UUID uploadedBy,
        String uploaderName,
        String originalFileName,
        long sizeBytes,
        boolean hasAbletonProject,
        @Nullable Long durationMs,
        @Nullable Double bpmEstimated,
        @Nullable Double bpmOverride,
        @Nullable Double loudnessLufs,
        @Nullable Double truePeakDbtp,
        @Nullable String failureReason,
        Instant createdAt,
        @Nullable Instant confirmedAt) {

    static final String UNKNOWN_USER = "Deleted user";

    static VersionView of(TrackVersion version, Map<UUID, String> displayNames) {
        return new VersionView(
                version.getId(),
                version.getProjectId(),
                version.getNumber(),
                version.getStatus(),
                version.getTitle(),
                version.getNotes(),
                version.getUploadedBy(),
                displayNames.getOrDefault(version.getUploadedBy(), UNKNOWN_USER),
                version.getOriginalFileName(),
                version.getSizeBytes(),
                version.hasAbletonProject(),
                version.getDurationMs(),
                version.getBpmEstimated(),
                version.getBpmOverride(),
                version.getLoudnessLufs(),
                version.getTruePeakDbtp(),
                version.getFailureReason(),
                version.getCreatedAt(),
                version.getConfirmedAt());
    }

    /** The BPM to show: the informed one, else the estimate. */
    public @Nullable Double effectiveBpm() {
        return bpmOverride != null ? bpmOverride : bpmEstimated;
    }
}
