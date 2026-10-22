package dev.higor.trackflow.api.processing.application;

import dev.higor.trackflow.api.version.TrackVersions;
import dev.higor.trackflow.api.version.VersionConfirmed;
import dev.higor.trackflow.contracts.ProcessAudioJob;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns a confirmed version into a job for the worker.
 *
 * <p>Runs after the confirmation commits, in its own transaction. If publishing fails, the
 * exception rolls back {@code startProcessing} and leaves the event publication incomplete, so
 * Modulith resubmits it later. If the commit fails after the broker accepted the message, the
 * worker's result carries a job id the version never stored and is ignored; the resubmission then
 * creates a fresh job. At-least-once delivery plus idempotent consumers, no distributed transaction.
 *
 * <p>The Ableton job follows the same pattern when {@link VersionConfirmed#abletonKey()} is set.
 */
@Component
class VersionJobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(VersionJobDispatcher.class);

    private final TrackVersions versions;
    private final JobPublisher jobs;
    private final Clock clock;

    VersionJobDispatcher(TrackVersions versions, JobPublisher jobs, Clock clock) {
        this.versions = versions;
        this.jobs = jobs;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(VersionConfirmed event) {
        UUID jobId = UUID.randomUUID();
        versions.startProcessing(event.versionId(), jobId);
        jobs.publish(new ProcessAudioJob(
                UUID.randomUUID(), jobId, event.versionId(), event.projectId(), event.sourceKey(), clock.instant()));
        log.info("Audio job dispatched versionId={} jobId={}", event.versionId(), jobId);
    }
}
