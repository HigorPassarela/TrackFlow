package dev.higor.trackflow.worker.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.higor.trackflow.contracts.AudioProcessed;
import dev.higor.trackflow.contracts.ProcessAudioJob;
import dev.higor.trackflow.contracts.Topology;
import dev.higor.trackflow.worker.audio.AudioJobProcessor;
import dev.higor.trackflow.worker.audio.AudioJobProcessor.ProcessedAudio;
import dev.higor.trackflow.worker.audio.InvalidMediaException;
import dev.higor.trackflow.worker.support.IntegrationTest;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * How failures are classified (design D7), with the real broker and listener container. The
 * pipeline is replaced so each test decides how the attempts fail; the retry intervals are
 * shortened by {@link IntegrationTest}.
 */
@IntegrationTest
class JobRetryIT {

    private static final long TIMEOUT_MS = 15_000;
    private static final String AUDIO_DLQ = Topology.deadLetterQueueOf(Topology.WORKER_AUDIO_PROCESS_QUEUE);

    @MockitoBean
    private AudioJobProcessor processor;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin admin;

    @BeforeEach
    void emptyQueues() {
        admin.purgeQueue(Topology.API_AUDIO_PROCESSED_QUEUE, false);
        admin.purgeQueue(AUDIO_DLQ, false);
    }

    @Test
    @DisplayName("Scenario: storage unavailable for a moment")
    void temporaryFailureIsRetriedAndSucceeds() {
        ProcessAudioJob job = job();
        given(processor.process(any()))
                .willThrow(new UncheckedIOException("storage unavailable", new ConnectException()))
                .willReturn(new ProcessedAudio("preview.mp3", "peaks.json", 1000, 124.0, -9.0, -1.0));

        AudioProcessed result = send(job);

        assertThat(result).isInstanceOf(AudioProcessed.Succeeded.class);
        verify(processor, times(2)).process(any());
        assertThat(rabbit.receive(AUDIO_DLQ, 500)).isNull();
    }

    @Test
    @DisplayName("Scenario: persistent failure")
    void failsAfterThreeAttemptsReportsAndDeadLetters() {
        ProcessAudioJob job = job();
        given(processor.process(any())).willThrow(new IllegalStateException("ffmpeg crashed"));

        AudioProcessed result = send(job);

        assertThat(result)
                .isInstanceOfSatisfying(
                        AudioProcessed.Failed.class,
                        failed -> assertThat(failed.reason()).isEqualTo("processing-error"));
        verify(processor, times(3)).process(any());
        ProcessAudioJob deadLettered =
                rabbit.receiveAndConvert(AUDIO_DLQ, TIMEOUT_MS, new ParameterizedTypeReference<>() {});
        assertThat(deadLettered).isNotNull();
        assertThat(deadLettered.jobId()).isEqualTo(job.jobId());
    }

    @Test
    @DisplayName("Scenario: corrupted file")
    void invalidFileFailsAtOnceWithoutRetry() {
        ProcessAudioJob job = job();
        given(processor.process(any()))
                .willThrow(new InvalidMediaException(InvalidMediaException.UNREADABLE_FILE, "not media"));

        AudioProcessed result = send(job);

        assertThat(result)
                .isInstanceOfSatisfying(
                        AudioProcessed.Failed.class,
                        failed -> assertThat(failed.reason()).isEqualTo("unreadable-file"));
        verify(processor, times(1)).process(any());
        assertThat(rabbit.receive(AUDIO_DLQ, 500)).isNull();
    }

    private static ProcessAudioJob job() {
        UUID versionId = UUID.randomUUID();
        return new ProcessAudioJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                versionId,
                UUID.randomUUID(),
                "projects/p/versions/" + versionId + "/source.wav",
                Instant.now());
    }

    private AudioProcessed send(ProcessAudioJob job) {
        rabbit.convertAndSend(Topology.JOBS_EXCHANGE, Topology.AUDIO_PROCESS_ROUTING_KEY, job);
        AudioProcessed result = rabbit.receiveAndConvert(
                Topology.API_AUDIO_PROCESSED_QUEUE, TIMEOUT_MS, new ParameterizedTypeReference<>() {});
        assertThat(result).as("result for jobId=" + job.jobId()).isNotNull();
        assertThat(result.jobId()).isEqualTo(job.jobId());
        return result;
    }
}
