package dev.higor.trackflow.api.processing.application;

import dev.higor.trackflow.api.ableton.AbletonAnalyses;
import dev.higor.trackflow.api.version.TrackVersions;
import dev.higor.trackflow.api.version.VersionConfirmed;
import dev.higor.trackflow.api.version.VersionReprocessRequested;
import dev.higor.trackflow.contracts.AnalyzeAbletonProjectJob;
import dev.higor.trackflow.contracts.ProcessAudioJob;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns a confirmed (or reprocessed) version into jobs for the worker.
 *
 * <p>Runs after the confirmation commits, in its own transaction. If publishing fails, the
 * exception rolls back {@code startProcessing} and leaves the event publication incomplete, so
 * Modulith resubmits it later. If the commit fails after the broker accepted the message, the
 * worker's result carries a job id the version never stored and is ignored; the resubmission then
 * creates a fresh job. At-least-once delivery plus idempotent consumers, no distributed transaction.
 */
@Component
class VersionJobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(VersionJobDispatcher.class);

    private final TrackVersions versions;
    private final AbletonAnalyses analyses;
    private final JobPublisher jobs;
    private final Clock clock;

    VersionJobDispatcher(TrackVersions versions, AbletonAnalyses analyses, JobPublisher jobs, Clock clock) {
        this.versions = versions;
        this.analyses = analyses;
        this.jobs = jobs;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(VersionConfirmed event) {
        dispatchAudio(event.versionId(), event.projectId(), event.sourceKey());
        if (event.abletonKey() != null) {
            UUID jobId = UUID.randomUUID();
            analyses.registerPending(event.versionId(), event.projectId(), jobId);
            jobs.publish(new AnalyzeAbletonProjectJob(
                    UUID.randomUUID(),
                    jobId,
                    event.versionId(),
                    event.projectId(),
                    event.abletonKey(),
                    clock.instant()));
            log.info("Ableton job dispatched versionId={} jobId={}", event.versionId(), jobId);
        }
    }

    /** Only the audio is processed again; the Ableton analysis keeps its own result. */
    @ApplicationModuleListener
    void on(VersionReprocessRequested event) {
        dispatchAudio(event.versionId(), event.projectId(), event.sourceKey());
    }

    private void dispatchAudio(UUID versionId, UUID projectId, String sourceKey) {
        UUID jobId = UUID.randomUUID();
        versions.startProcessing(versionId, jobId);
        jobs.publish(new ProcessAudioJob(UUID.randomUUID(), jobId, versionId, projectId, sourceKey, clock.instant()));
        log.info("Audio job dispatched versionId={} jobId={}", versionId, jobId);
    }
}
