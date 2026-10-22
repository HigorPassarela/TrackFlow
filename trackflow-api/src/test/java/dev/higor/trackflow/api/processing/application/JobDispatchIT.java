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
import dev.higor.trackflow.contracts.AnalyzeAbletonProjectJob;
import dev.higor.trackflow.contracts.ProcessAudioJob;
import dev.higor.trackflow.contracts.Topology;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/** Confirmation in the API, job on the worker's queue; this test plays the worker by reading it. */
@IntegrationTest
@Import(Fixtures.class)
class JobDispatchIT {

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

    private User editor;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        admin.purgeQueue(Topology.WORKER_AUDIO_PROCESS_QUEUE, false);
        admin.purgeQueue(Topology.WORKER_ABLETON_ANALYZE_QUEUE, false);
        editor = fixtures.user("dispatch");
        projectId = fixtures.projectOwnedBy(editor);
    }

    @Test
    void confirmationSendsAnAudioJobAndStartsProcessing() {
        UUID versionId = confirmedVersion(null);

        ProcessAudioJob job = rabbit.receiveAndConvert(
                Topology.WORKER_AUDIO_PROCESS_QUEUE, RECEIVE_TIMEOUT_MS, new ParameterizedTypeReference<>() {});

        assertThat(job).isNotNull();
        assertThat(job.versionId()).isEqualTo(versionId);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(versionRows.findById(versionId)).hasValueSatisfying(version -> {
                    assertThat(version.getStatus()).isEqualTo(VersionStatus.PROCESSING);
                    assertThat(version.getProcessingJobId()).isEqualTo(job.jobId());
                }));
    }

    @Test
    void confirmationWithAbletonProjectAlsoSendsTheAnalysisAndMarksItPending() {
        UUID versionId = confirmedVersion(new RequestUpload.AbletonAttachment("ibiza.als", SIZE));

        AnalyzeAbletonProjectJob job = rabbit.receiveAndConvert(
                Topology.WORKER_ABLETON_ANALYZE_QUEUE, RECEIVE_TIMEOUT_MS, new ParameterizedTypeReference<>() {});

        assertThat(job).isNotNull();
        assertThat(job.alsKey()).endsWith("/project.als");
        assertThat(analyses.findById(versionId)).hasValueSatisfying(analysis -> {
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PENDING);
            assertThat(analysis.getJobId()).isEqualTo(job.jobId());
        });
    }

    private UUID confirmedVersion(RequestUpload.@Nullable AbletonAttachment ableton) {
        UploadTicket ticket = versions.requestUpload(
                new RequestUpload(projectId, editor.getId(), "bounce.wav", "audio/wav", SIZE, ableton, null, null));
        put(ticket.source().url().getPath());
        if (ticket.ableton() != null) {
            put(ticket.ableton().url().getPath());
        }
        return versions.confirm(projectId, ticket.version().id(), editor.getId())
                .id();
    }

    private void put(String urlPath) {
        String key = urlPath.replaceFirst("^/" + SeaweedFsTestConfiguration.BUCKET + "/", "");
        s3.putObject(
                put -> put.bucket(SeaweedFsTestConfiguration.BUCKET).key(key), RequestBody.fromBytes(new byte[SIZE]));
    }
}
