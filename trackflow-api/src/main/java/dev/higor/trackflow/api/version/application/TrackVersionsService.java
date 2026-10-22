package dev.higor.trackflow.api.version.application;

import dev.higor.trackflow.api.version.ReadyMedia;
import dev.higor.trackflow.api.version.TrackVersions;
import dev.higor.trackflow.api.version.VersionStatusChanged;
import dev.higor.trackflow.api.version.VersionSummary;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What other modules (processing, comment) may ask of or tell the version module. */
@Service
class TrackVersionsService implements TrackVersions {

    private static final Logger log = LoggerFactory.getLogger(TrackVersionsService.class);

    private final TrackVersionRepository versions;
    private final ApplicationEventPublisher events;
    private final ProcessingMetrics metrics;
    private final Clock clock;

    TrackVersionsService(
            TrackVersionRepository versions, ApplicationEventPublisher events, ProcessingMetrics metrics, Clock clock) {
        this.versions = versions;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VersionSummary> findSummary(UUID projectId, UUID versionId) {
        return versions.findByIdAndProjectId(versionId, projectId)
                .filter(TrackVersion::isConfirmed)
                .map(version -> new VersionSummary(
                        version.getId(),
                        version.getProjectId(),
                        version.getNumber(),
                        version.getStatus(),
                        version.getDurationMs()));
    }

    @Override
    @Transactional
    public void startProcessing(UUID versionId, UUID jobId) {
        TrackVersion version = require(versionId);
        version.startProcessing(jobId, clock.instant());
        statusChanged(version);
        log.info("Processing started versionId={} jobId={}", versionId, jobId);
    }

    @Override
    @Transactional
    public void completeProcessing(UUID versionId, UUID jobId, ReadyMedia media) {
        TrackVersion version = require(versionId);
        Instant now = clock.instant();
        boolean applied = version.complete(jobId, media, now);
        if (applied) {
            statusChanged(version);
            metrics.succeeded(version, now);
            log.info("Version ready versionId={} jobId={} durationMs={}", versionId, jobId, media.durationMs());
        } else {
            log.debug("Ignored stale or duplicated success versionId={} jobId={}", versionId, jobId);
        }
    }

    @Override
    @Transactional
    public void failProcessing(UUID versionId, UUID jobId, String reason) {
        TrackVersion version = require(versionId);
        Instant now = clock.instant();
        boolean applied = version.fail(jobId, reason, now);
        if (applied) {
            statusChanged(version);
            metrics.failed(version, reason, now);
            log.info("Version failed versionId={} jobId={} reason={}", versionId, jobId, reason);
        } else {
            log.debug("Ignored stale or duplicated failure versionId={} jobId={}", versionId, jobId);
        }
    }

    /** Only applied transitions are announced, so a duplicated result notifies members once. */
    private void statusChanged(TrackVersion version) {
        events.publishEvent(new VersionStatusChanged(
                version.getId(), version.getProjectId(), version.getStatus(), version.getUpdatedAt()));
    }

    /**
     * Locks the row for the rest of the transaction. The dispatcher starts processing and sends the
     * job in one transaction, and the change only reaches the database at commit; a worker can answer
     * before that. Because both sides lock the row, the result waits for the dispatch to commit and
     * then finds the version processing with its job, instead of discarding the result as stale.
     *
     * <p>Results only come for versions this API created, so a missing one is a bug, not a user error.
     */
    private TrackVersion require(UUID versionId) {
        return versions.findByIdForUpdate(versionId)
                .orElseThrow(() -> new IllegalStateException("Unknown version versionId=" + versionId));
    }
}
