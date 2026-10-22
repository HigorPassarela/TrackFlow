package dev.higor.trackflow.api.ableton.application;

import dev.higor.trackflow.api.ableton.domain.AbletonAnalysis;
import dev.higor.trackflow.api.ableton.domain.AbletonAnalysisRepository;
import dev.higor.trackflow.api.ableton.domain.AbletonDiff;
import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What members read about Ableton projects. Any member may read, listeners included: seeing how a
 * track was built is not the same as downloading the {@code .als}.
 *
 * <p>Analyses are always looked up by project and version. A version of another project and a
 * version without a project file get the same 404, which names the version: the caller learns
 * which version has no analysis, and nothing about other people's projects.
 */
@Service
public class AbletonQueryService {

    private final AbletonAnalysisRepository analyses;
    private final ProjectAccess access;

    public AbletonQueryService(AbletonAnalysisRepository analyses, ProjectAccess access) {
        this.analyses = analyses;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public AnalysisView find(UUID projectId, UUID versionId, UUID userId) {
        access.requireRole(projectId, userId, ProjectRole.LISTENER);
        return AnalysisView.of(require(projectId, versionId));
    }

    /** Changes from {@code againstVersionId} (the older one, usually) to {@code versionId}. */
    @Transactional(readOnly = true)
    public AbletonDiff diff(UUID projectId, UUID versionId, UUID againstVersionId, UUID userId) {
        access.requireRole(projectId, userId, ProjectRole.LISTENER);
        AbletonAnalysis from = requireDone(projectId, againstVersionId);
        AbletonAnalysis to = requireDone(projectId, versionId);
        return AbletonDiff.between(from.snapshot(), to.snapshot());
    }

    private AbletonAnalysis require(UUID projectId, UUID versionId) {
        return analyses.findByVersionIdAndProjectId(versionId, projectId)
                .orElseThrow(() -> new NotFoundException(
                        "ableton-analysis-not-found",
                        "Version %s has no Ableton project analysis".formatted(versionId)));
    }

    private AbletonAnalysis requireDone(UUID projectId, UUID versionId) {
        AbletonAnalysis analysis = require(projectId, versionId);
        if (!analysis.isDone()) {
            throw new BusinessRuleException(
                    "ableton-analysis-unavailable",
                    "The Ableton project of version %s is %s, not analyzed"
                            .formatted(versionId, analysis.getStatus().name().toLowerCase(Locale.ROOT)));
        }
        return analysis;
    }
}
