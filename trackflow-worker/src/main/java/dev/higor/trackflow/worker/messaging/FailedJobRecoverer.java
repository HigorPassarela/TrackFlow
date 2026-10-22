package dev.higor.trackflow.worker.messaging;

import dev.higor.trackflow.contracts.AbletonAnalyzed;
import dev.higor.trackflow.contracts.AnalyzeAbletonProjectJob;
import dev.higor.trackflow.contracts.AudioProcessed;
import dev.higor.trackflow.contracts.ProcessAudioJob;
import dev.higor.trackflow.contracts.Topology;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs when a job failed all its attempts (design D7): tells the API, so the version leaves
 * "processing" and can be reprocessed (or the analysis stops being pending), then rejects the message so the broker moves it to the
 * queue's DLQ for inspection.
 *
 * <p>Spring Boot plugs this bean into the listener retry. The job is read from the body by queue
 * because the converter's type headers are not trusted here.
 */
@Component
class FailedJobRecoverer implements MessageRecoverer {

    static final String PROCESSING_ERROR = "processing-error";

    private static final Logger log = LoggerFactory.getLogger(FailedJobRecoverer.class);

    private final ResultPublisher results;
    private final JsonMapper json;
    private final Clock clock;

    FailedJobRecoverer(ResultPublisher results, JsonMapper json, Clock clock) {
        this.results = results;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        String queue = message.getMessageProperties().getConsumerQueue();
        if (Topology.WORKER_AUDIO_PROCESS_QUEUE.equals(queue)) {
            ProcessAudioJob job = json.readValue(message.getBody(), ProcessAudioJob.class);
            log.error("Audio job failed after retries versionId={} jobId={}", job.versionId(), job.jobId(), cause);
            results.publish(new AudioProcessed.Failed(
                    UUID.randomUUID(), job.jobId(), job.versionId(), clock.instant(), PROCESSING_ERROR));
        } else if (Topology.WORKER_ABLETON_ANALYZE_QUEUE.equals(queue)) {
            AnalyzeAbletonProjectJob job = json.readValue(message.getBody(), AnalyzeAbletonProjectJob.class);
            log.error("Ableton job failed after retries versionId={} jobId={}", job.versionId(), job.jobId(), cause);
            results.publish(new AbletonAnalyzed.Failed(
                    UUID.randomUUID(), job.jobId(), job.versionId(), clock.instant(), PROCESSING_ERROR));
        } else {
            log.error("Job failed after retries queue={}", queue, cause);
        }
        throw new AmqpRejectAndDontRequeueException("Job failed after retries, sent to the DLQ", cause);
    }
}
