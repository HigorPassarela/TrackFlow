package dev.higor.trackflow.api.ableton.application;

import dev.higor.trackflow.api.ableton.AbletonAnalyses;
import dev.higor.trackflow.api.ableton.domain.AbletonAnalysis;
import dev.higor.trackflow.api.ableton.domain.AbletonAnalysisRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AbletonAnalysesService implements AbletonAnalyses {

    private static final Logger log = LoggerFactory.getLogger(AbletonAnalysesService.class);

    private final AbletonAnalysisRepository analyses;
    private final Clock clock;

    AbletonAnalysesService(AbletonAnalysisRepository analyses, Clock clock) {
        this.analyses = analyses;
        this.clock = clock;
    }

    /** A resubmitted confirmation event never creates a second analysis. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerPending(UUID versionId, UUID projectId, UUID jobId) {
        Optional<AbletonAnalysis> existing = analyses.findById(versionId);
        if (existing.isPresent()) {
            existing.get().reassignPendingJob(jobId);
            return;
        }
        analyses.save(AbletonAnalysis.builder()
                .versionId(versionId)
                .projectId(projectId)
                .jobId(jobId)
                .createdAt(clock.instant())
                .build());
        log.info("Ableton analysis pending versionId={} jobId={}", versionId, jobId);
    }

    @Override
    @Transactional
    public void complete(UUID versionId, UUID jobId, ProjectSnapshot snapshot) {
        boolean applied = analyses.findById(versionId)
                .map(analysis -> analysis.complete(jobId, snapshot, clock.instant()))
                .orElse(false);
        if (applied) {
            log.info("Ableton analysis done versionId={} jobId={}", versionId, jobId);
        } else {
            log.debug("Ignored stale or duplicated analysis versionId={} jobId={}", versionId, jobId);
        }
    }

    @Override
    @Transactional
    public void fail(UUID versionId, UUID jobId, String reason) {
        boolean applied = analyses.findById(versionId)
                .map(analysis -> analysis.fail(jobId, reason, clock.instant()))
                .orElse(false);
        if (applied) {
            log.info("Ableton analysis failed versionId={} jobId={} reason={}", versionId, jobId, reason);
        } else {
            log.debug("Ignored stale or duplicated analysis failure versionId={} jobId={}", versionId, jobId);
        }
    }
}
