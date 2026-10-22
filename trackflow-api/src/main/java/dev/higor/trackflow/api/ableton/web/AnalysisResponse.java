package dev.higor.trackflow.api.ableton.web;

import dev.higor.trackflow.api.ableton.AbletonAnalyses.ProjectSnapshot;
import dev.higor.trackflow.api.ableton.application.AnalysisView;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * @param status {@code pending}, {@code done} or {@code failed}
 * @param project filled when {@code status} is {@code done}
 */
public record AnalysisResponse(
        UUID versionId,
        String status,
        @Nullable String failureReason,
        @Nullable Project project,
        @Nullable Instant analyzedAt) {

    /**
     * @param verifiedVersion {@code false} means a Live version other than 11 or 12, read on a best
     *     effort basis
     */
    public record Project(
            @Nullable String liveVersion,
            boolean verifiedVersion,
            @Nullable Double tempo,
            @Nullable String timeSignature,
            List<Track> tracks) {

        static @Nullable Project from(@Nullable ProjectSnapshot snapshot) {
            if (snapshot == null) {
                return null;
            }
            return new Project(
                    snapshot.liveVersion(),
                    snapshot.verifiedVersion(),
                    snapshot.tempo(),
                    snapshot.timeSignature(),
                    snapshot.tracks().stream()
                            .map(track ->
                                    new Track(track.id(), track.type(), track.name(), track.devices(), track.plugins()))
                            .toList());
        }
    }

    public record Track(String id, String type, String name, List<String> devices, List<String> plugins) {}

    static AnalysisResponse from(AnalysisView view) {
        return new AnalysisResponse(
                view.versionId(),
                view.status().name().toLowerCase(Locale.ROOT),
                view.failureReason(),
                Project.from(view.project()),
                view.analyzedAt());
    }
}
