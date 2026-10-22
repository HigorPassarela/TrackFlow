package dev.higor.trackflow.api.processing.adapter;

import dev.higor.trackflow.api.version.ReadyMedia;
import dev.higor.trackflow.api.version.TrackVersions;
import dev.higor.trackflow.contracts.AudioProcessed;
import dev.higor.trackflow.contracts.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: worker results become calls to the version module's public API. Duplicates and
 * late results are safe because {@link TrackVersions} checks the job id.
 */
@Component
class AudioProcessedListener {

    private static final Logger log = LoggerFactory.getLogger(AudioProcessedListener.class);

    private final TrackVersions versions;

    AudioProcessedListener(TrackVersions versions) {
        this.versions = versions;
    }

    @RabbitListener(queues = Topology.API_AUDIO_PROCESSED_QUEUE)
    void on(AudioProcessed result) {
        switch (result) {
            case AudioProcessed.Succeeded succeeded ->
                versions.completeProcessing(
                        succeeded.versionId(),
                        succeeded.jobId(),
                        new ReadyMedia(
                                succeeded.previewKey(),
                                succeeded.peaksKey(),
                                succeeded.durationMs(),
                                succeeded.bpmEstimated(),
                                succeeded.loudnessLufs(),
                                succeeded.truePeakDbtp()));
            case AudioProcessed.Failed failed ->
                versions.failProcessing(failed.versionId(), failed.jobId(), failed.reason());
        }
        log.info(
                "Audio result received versionId={} jobId={} outcome={}",
                result.versionId(),
                result.jobId(),
                result.getClass().getSimpleName());
    }
}
