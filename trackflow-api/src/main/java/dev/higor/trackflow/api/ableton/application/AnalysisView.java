package dev.higor.trackflow.api.ableton.application;

import dev.higor.trackflow.api.ableton.AbletonAnalyses.ProjectSnapshot;
import dev.higor.trackflow.api.ableton.domain.AbletonAnalysis;
import dev.higor.trackflow.api.ableton.domain.AnalysisStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An analysis as members see it. The snapshot is only filled once the analysis is done; a failure
 * carries its reason instead.
 */
public record AnalysisView(
        UUID versionId,
        AnalysisStatus status,
        @Nullable String failureReason,
        @Nullable ProjectSnapshot project,
        @Nullable Instant analyzedAt) {

    static AnalysisView of(AbletonAnalysis analysis) {
        return new AnalysisView(
                analysis.getVersionId(),
                analysis.getStatus(),
                analysis.getFailureReason(),
                analysis.isDone() ? analysis.snapshot() : null,
                analysis.getAnalyzedAt());
    }
}
