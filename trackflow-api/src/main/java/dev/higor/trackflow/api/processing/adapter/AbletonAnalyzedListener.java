package dev.higor.trackflow.api.processing.adapter;

import dev.higor.trackflow.api.ableton.AbletonAnalyses;
import dev.higor.trackflow.api.ableton.AbletonAnalyses.ProjectSnapshot;
import dev.higor.trackflow.api.ableton.AbletonAnalyses.Track;
import dev.higor.trackflow.contracts.AbletonAnalyzed;
import dev.higor.trackflow.contracts.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter for Ableton results. Never touches the version: a failed analysis only marks the
 * analysis as failed, so the version still becomes ready.
 */
@Component
class AbletonAnalyzedListener {

    private static final Logger log = LoggerFactory.getLogger(AbletonAnalyzedListener.class);

    private final AbletonAnalyses analyses;

    AbletonAnalyzedListener(AbletonAnalyses analyses) {
        this.analyses = analyses;
    }

    @RabbitListener(queues = Topology.API_ABLETON_ANALYZED_QUEUE)
    void on(AbletonAnalyzed result) {
        switch (result) {
            case AbletonAnalyzed.Succeeded succeeded ->
                analyses.complete(
                        succeeded.versionId(),
                        succeeded.jobId(),
                        new ProjectSnapshot(
                                succeeded.liveVersion(),
                                succeeded.verifiedVersion(),
                                succeeded.tempo(),
                                succeeded.timeSignature(),
                                succeeded.tracks().stream()
                                        .map(track -> new Track(
                                                track.id(),
                                                track.type(),
                                                track.name(),
                                                track.devices(),
                                                track.plugins()))
                                        .toList()));
            case AbletonAnalyzed.Failed failed -> analyses.fail(failed.versionId(), failed.jobId(), failed.reason());
        }
        log.info(
                "Ableton result received versionId={} jobId={} outcome={}",
                result.versionId(),
                result.jobId(),
                result.getClass().getSimpleName());
    }
}
