package dev.higor.trackflow.worker.messaging;

import dev.higor.trackflow.contracts.AbletonAnalyzed;
import dev.higor.trackflow.contracts.AnalyzeAbletonProjectJob;
import dev.higor.trackflow.contracts.Topology;
import dev.higor.trackflow.worker.ableton.AbletonJobProcessor;
import dev.higor.trackflow.worker.ableton.AbletonProject;
import dev.higor.trackflow.worker.ableton.InvalidProjectException;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Same rules as {@link ProcessAudioJobListener}: one result per job, bad files fail at once. */
@Component
class AnalyzeAbletonProjectJobListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeAbletonProjectJobListener.class);

    private final AbletonJobProcessor processor;
    private final ResultPublisher results;
    private final Clock clock;

    AnalyzeAbletonProjectJobListener(AbletonJobProcessor processor, ResultPublisher results, Clock clock) {
        this.processor = processor;
        this.results = results;
        this.clock = clock;
    }

    @RabbitListener(queues = Topology.WORKER_ABLETON_ANALYZE_QUEUE)
    void on(AnalyzeAbletonProjectJob job) {
        log.info("Ableton job received versionId={} jobId={}", job.versionId(), job.jobId());
        AbletonAnalyzed result;
        try {
            AbletonProject project = processor.process(job);
            result = new AbletonAnalyzed.Succeeded(
                    UUID.randomUUID(),
                    job.jobId(),
                    job.versionId(),
                    clock.instant(),
                    project.liveVersion(),
                    project.verifiedVersion(),
                    project.tempo(),
                    project.timeSignature(),
                    project.tracks());
        } catch (InvalidProjectException invalid) {
            log.info(
                    "Ableton job rejected versionId={} jobId={} reason={}",
                    job.versionId(),
                    job.jobId(),
                    invalid.reason());
            result = new AbletonAnalyzed.Failed(
                    UUID.randomUUID(), job.jobId(), job.versionId(), clock.instant(), invalid.reason());
        }
        results.publish(result);
    }
}
