package dev.higor.trackflow.worker.messaging;

import dev.higor.trackflow.contracts.AudioProcessed;
import dev.higor.trackflow.contracts.ProcessAudioJob;
import dev.higor.trackflow.contracts.Topology;
import dev.higor.trackflow.worker.audio.AudioJobProcessor;
import dev.higor.trackflow.worker.audio.AudioJobProcessor.ProcessedAudio;
import dev.higor.trackflow.worker.audio.InvalidMediaException;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Turns a job into exactly one result. A bad file becomes a failure result right away; any other
 * exception escapes so the container retries the job, and after the last attempt {@link
 * FailedJobRecoverer} reports it.
 */
@Component
class ProcessAudioJobListener {

    private static final Logger log = LoggerFactory.getLogger(ProcessAudioJobListener.class);

    private final AudioJobProcessor processor;
    private final ResultPublisher results;
    private final Clock clock;

    ProcessAudioJobListener(AudioJobProcessor processor, ResultPublisher results, Clock clock) {
        this.processor = processor;
        this.results = results;
        this.clock = clock;
    }

    @RabbitListener(queues = Topology.WORKER_AUDIO_PROCESS_QUEUE)
    void on(ProcessAudioJob job) {
        log.info("Audio job received versionId={} jobId={}", job.versionId(), job.jobId());
        AudioProcessed result;
        try {
            ProcessedAudio audio = processor.process(job);
            result = new AudioProcessed.Succeeded(
                    UUID.randomUUID(),
                    job.jobId(),
                    job.versionId(),
                    clock.instant(),
                    audio.previewKey(),
                    audio.peaksKey(),
                    audio.durationMs(),
                    audio.bpmEstimated(),
                    audio.loudnessLufs(),
                    audio.truePeakDbtp());
        } catch (InvalidMediaException invalid) {
            log.info(
                    "Audio job rejected versionId={} jobId={} reason={}",
                    job.versionId(),
                    job.jobId(),
                    invalid.reason());
            result = new AudioProcessed.Failed(
                    UUID.randomUUID(), job.jobId(), job.versionId(), clock.instant(), invalid.reason());
        }
        results.publish(result);
    }
}
