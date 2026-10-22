package dev.higor.trackflow.worker.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.higor.trackflow.contracts.AudioProcessed;
import dev.higor.trackflow.contracts.ProcessAudioJob;
import dev.higor.trackflow.contracts.Topology;
import dev.higor.trackflow.worker.audio.FfmpegTestFiles;
import dev.higor.trackflow.worker.audio.TestSignals;
import dev.higor.trackflow.worker.storage.ObjectStorage;
import dev.higor.trackflow.worker.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The worker end to end: a job arrives on its queue, the file comes from SeaweedFS, ffmpeg runs for
 * real, outputs go back to SeaweedFS and the result lands where the API listens.
 */
@IntegrationTest
@Tag("ffmpeg")
class ProcessAudioJobIT {

    private static final long RESULT_TIMEOUT_MS = 60_000;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin admin;

    @Autowired
    private ObjectStorage storage;

    @Autowired
    private JsonMapper json;

    @TempDir
    Path directory;

    @BeforeEach
    void emptyResults() {
        admin.purgeQueue(Topology.API_AUDIO_PROCESSED_QUEUE, false);
    }

    @Test
    @DisplayName("Scenario: success flow")
    void processesAWavIntoPreviewPeaksAndMetrics() throws Exception {
        Path wav = TestSignals.writeWav(directory.resolve("bounce.wav"), TestSignals.metronome(124, 20));
        ProcessAudioJob job = jobFor(wav, "wav");

        AudioProcessed.Succeeded result = (AudioProcessed.Succeeded) send(job);

        assertThat(result.jobId()).isEqualTo(job.jobId());
        assertThat(result.durationMs()).isEqualTo(20_000);
        assertThat(result.bpmEstimated()).isCloseTo(124, within(1.0));
        assertThat(result.previewKey()).isEqualTo(prefixOf(job) + "preview.mp3");
        JsonNode peaks = download(result.peaksKey());
        assertThat(peaks.get("pointsPerSecond").asInt()).isEqualTo(30);
        assertThat(peaks.get("peaks").size()).isEqualTo(20 * 30);
        assertThat(Files.size(downloadFile(result.previewKey()))).isPositive();
    }

    @Test
    @DisplayName("Scenario: screen recording")
    void processesTheAudioOfAnMp4() throws Exception {
        Path wav = TestSignals.writeWav(directory.resolve("ableton.wav"), TestSignals.metronome(128, 10));
        Path video = FfmpegTestFiles.videoWithAudio(directory.resolve("screen.mp4"), wav);
        ProcessAudioJob job = jobFor(video, "mp4");

        AudioProcessed result = send(job);

        assertThat(result).isInstanceOfSatisfying(AudioProcessed.Succeeded.class, succeeded -> {
            assertThat(succeeded.durationMs()).isCloseTo(10_000, within(100L));
            assertThat(succeeded.peaksKey()).isEqualTo(prefixOf(job) + "peaks.json");
        });
    }

    @Test
    @DisplayName("Scenario: video without audio")
    void reportsAVideoWithoutAudioAsFailedWithItsReason() {
        Path video = FfmpegTestFiles.videoWithoutAudio(directory.resolve("screen.mp4"));

        AudioProcessed result = send(jobFor(video, "mp4"));

        assertThat(result)
                .isInstanceOfSatisfying(
                        AudioProcessed.Failed.class,
                        failed -> assertThat(failed.reason()).isEqualTo("no-audio-stream"));
    }

    private ProcessAudioJob jobFor(Path file, String extension) {
        UUID projectId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String sourceKey = "projects/%s/versions/%s/source.%s".formatted(projectId, versionId, extension);
        storage.upload(file, sourceKey, "application/octet-stream");
        return new ProcessAudioJob(
                UUID.randomUUID(), UUID.randomUUID(), versionId, projectId, sourceKey, Instant.now());
    }

    private AudioProcessed send(ProcessAudioJob job) {
        rabbit.convertAndSend(Topology.JOBS_EXCHANGE, Topology.AUDIO_PROCESS_ROUTING_KEY, job);
        AudioProcessed result = rabbit.receiveAndConvert(
                Topology.API_AUDIO_PROCESSED_QUEUE, RESULT_TIMEOUT_MS, new ParameterizedTypeReference<>() {});
        assertThat(result).as("result for jobId=" + job.jobId()).isNotNull();
        assertThat(result.versionId()).isEqualTo(job.versionId());
        return result;
    }

    private static String prefixOf(ProcessAudioJob job) {
        return job.sourceKey().substring(0, job.sourceKey().lastIndexOf('/') + 1);
    }

    private JsonNode download(String key) {
        return json.readTree(downloadFile(key).toFile());
    }

    private Path downloadFile(String key) {
        Path target = directory.resolve(key.substring(key.lastIndexOf('/') + 1));
        storage.download(key, target);
        return target;
    }
}
