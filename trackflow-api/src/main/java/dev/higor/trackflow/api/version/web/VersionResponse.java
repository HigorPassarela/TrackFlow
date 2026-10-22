package dev.higor.trackflow.api.version.web;

import dev.higor.trackflow.api.version.application.VersionView;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A version in the API. Metrics are {@code null} until the version is ready. The BPM always says
 * where it came from: {@code estimated} by the worker or {@code informed} by a member.
 */
public record VersionResponse(
        UUID id,
        @Nullable Integer number,
        String status,
        @Nullable String title,
        @Nullable String notes,
        Uploader uploadedBy,
        String originalFileName,
        long sizeBytes,
        boolean hasAbletonProject,
        @Nullable Metrics metrics,
        @Nullable String failureReason,
        Instant createdAt,
        @Nullable Instant confirmedAt) {

    public record Uploader(UUID id, String name) {}

    public record Metrics(
            @Nullable Long durationMs,
            @Nullable Double bpm,
            @Nullable String bpmSource,
            @Nullable Double bpmEstimated,
            @Nullable Double loudnessLufs,
            @Nullable Double truePeakDbtp) {}

    static VersionResponse from(VersionView view) {
        return new VersionResponse(
                view.id(),
                view.number(),
                view.status().name().toLowerCase(Locale.ROOT),
                view.title(),
                view.notes(),
                new Uploader(view.uploadedBy(), view.uploaderName()),
                view.originalFileName(),
                view.sizeBytes(),
                view.hasAbletonProject(),
                metrics(view),
                view.failureReason(),
                view.createdAt(),
                view.confirmedAt());
    }

    private static @Nullable Metrics metrics(VersionView view) {
        if (view.durationMs() == null) {
            return null;
        }
        String bpmSource = view.bpmOverride() != null ? "informed" : view.bpmEstimated() != null ? "estimated" : null;
        return new Metrics(
                view.durationMs(),
                view.effectiveBpm(),
                bpmSource,
                view.bpmEstimated(),
                view.loudnessLufs(),
                view.truePeakDbtp());
    }
}
