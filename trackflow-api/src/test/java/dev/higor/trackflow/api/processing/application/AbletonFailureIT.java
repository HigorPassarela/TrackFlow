package dev.higor.trackflow.api.processing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.higor.trackflow.api.ableton.domain.AbletonAnalysisRepository;
import dev.higor.trackflow.api.ableton.domain.AnalysisStatus;
import dev.higor.trackflow.api.identity.domain.User;
import dev.higor.trackflow.api.support.Fixtures;
import dev.higor.trackflow.api.support.IntegrationTest;
import dev.higor.trackflow.api.support.SeaweedFsTestConfiguration;
import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.application.RequestUpload;
import dev.higor.trackflow.api.version.application.UploadTicket;
import dev.higor.trackflow.api.version.application.VersionService;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import dev.higor.trackflow.contracts.AbletonAnalyzed;
import dev.higor.trackflow.contracts.AnalyzeAbletonProjectJob;
import dev.higor.trackflow.contracts.AudioProcessed;
import dev.higor.trackflow.contracts.ProcessAudioJob;
import dev.higor.trackflow.contracts.Topology;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The analysis has its own lifecycle (design D10): this test plays the worker, answering the audio
 * job with success and the Ableton job with failure, and checks that only the analysis failed.
 */
@IntegrationTest
@Import(Fixtures.class)
class AbletonFailureIT {

    private static final int SIZE = 2048;
    private static final long RECEIVE_TIMEOUT_MS = 10_000;

    @Autowired
    private VersionService versions;

    @Autowired
    private TrackVersionRepository versionRows;

    @Autowired
    private AbletonAnalysisRepository analyses;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin admin;

    @Autowired
    private S3Client s3;

    @Autowired
    private Fixtures fixtures;

    @BeforeEach
    void emptyWorkerQueues() {
        admin.purgeQueue(Topology.WORKER_AUDIO_PROCESS_QUEUE, false);
        admin.purgeQueue(Topology.WORKER_ABLETON_ANALYZE_QUEUE, false);
    }

    @Test
    @DisplayName("Scenario: corrupted project")
    void failedAnalysisLeavesTheVersionReady() {
        UUID versionId = confirmedVersionWithAbletonProject();
        ProcessAudioJob audioJob = rabbit.receiveAndConvert(
                Topology.WORKER_AUDIO_PROCESS_QUEUE, RECEIVE_TIMEOUT_MS, new ParameterizedTypeReference<>() {});
        AnalyzeAbletonProjectJob abletonJob = rabbit.receiveAndConvert(
                Topology.WORKER_ABLETON_ANALYZE_QUEUE, RECEIVE_TIMEOUT_MS, new ParameterizedTypeReference<>() {});
        assertThat(audioJob).isNotNull();
        assertThat(abletonJob).isNotNull();

        rabbit.convertAndSend(
                Topology.RESULTS_EXCHANGE,
                Topology.ABLETON_ANALYZED_ROUTING_KEY,
                new AbletonAnalyzed.Failed(
                        UUID.randomUUID(), abletonJob.jobId(), versionId, Instant.now(), "invalid-project-file"));
        rabbit.convertAndSend(
                Topology.RESULTS_EXCHANGE,
                Topology.AUDIO_PROCESSED_ROUTING_KEY,
                new AudioProcessed.Succeeded(
                        UUID.randomUUID(),
                        audioJob.jobId(),
                        versionId,
                        Instant.now(),
                        "preview.mp3",
                        "peaks.json",
                        180_000,
                        124.0,
                        -8.5,
                        -0.4));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(analyses.findById(versionId)).hasValueSatisfying(analysis -> {
                assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
                assertThat(analysis.getFailureReason()).isEqualTo("invalid-project-file");
            });
            assertThat(versionRows.findById(versionId))
                    .hasValueSatisfying(
                            version -> assertThat(version.getStatus()).isEqualTo(VersionStatus.READY));
        });
    }

    private UUID confirmedVersionWithAbletonProject() {
        User editor = fixtures.user("als");
        UUID projectId = fixtures.projectOwnedBy(editor);
        UploadTicket ticket = versions.requestUpload(new RequestUpload(
                projectId,
                editor.getId(),
                "bounce.wav",
                "audio/wav",
                SIZE,
                new RequestUpload.AbletonAttachment("ibiza.als", SIZE),
                null,
                null));
        put(ticket.source().url().getPath());
        put(ticket.ableton().url().getPath());
        return versions.confirm(projectId, ticket.version().id(), editor.getId())
                .id();
    }

    private void put(String urlPath) {
        String key = urlPath.replaceFirst("^/" + SeaweedFsTestConfiguration.BUCKET + "/", "");
        s3.putObject(
                put -> put.bucket(SeaweedFsTestConfiguration.BUCKET).key(key), RequestBody.fromBytes(new byte[SIZE]));
    }
}
