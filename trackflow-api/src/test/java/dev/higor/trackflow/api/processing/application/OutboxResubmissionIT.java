package dev.higor.trackflow.api.processing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.higor.trackflow.api.identity.domain.User;
import dev.higor.trackflow.api.support.Fixtures;
import dev.higor.trackflow.api.support.IntegrationTest;
import dev.higor.trackflow.api.support.SeaweedFsTestConfiguration;
import dev.higor.trackflow.api.version.application.RequestUpload;
import dev.higor.trackflow.api.version.application.UploadTicket;
import dev.higor.trackflow.api.version.application.VersionService;
import dev.higor.trackflow.contracts.ProcessAudioJob;
import dev.higor.trackflow.contracts.Topology;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The broker is paused (not stopped: the mapped port must survive) while the confirmation's job is
 * sent. The confirmation still commits, the event publication stays incomplete, and the job reaches
 * the queue once the broker is back and the resubmission runs (design D6).
 */
@IntegrationTest
@Import(Fixtures.class)
class OutboxResubmissionIT {

    private static final int SIZE = 1024;

    @Autowired
    private VersionService versions;

    @Autowired
    private IncompleteEventPublications publications;

    @Autowired
    private RabbitMQContainer rabbitmq;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin admin;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private S3Client s3;

    @Autowired
    private Fixtures fixtures;

    @AfterEach
    void resumeBroker() {
        if (isPaused()) {
            rabbitmq.getDockerClient()
                    .unpauseContainerCmd(rabbitmq.getContainerId())
                    .exec();
        }
    }

    @Test
    void jobSentWhileTheBrokerIsDownArrivesAfterResubmission() {
        admin.purgeQueue(Topology.WORKER_AUDIO_PROCESS_QUEUE, false);
        User editor = fixtures.user("outbox");
        UUID projectId = fixtures.projectOwnedBy(editor);
        UploadTicket ticket = versions.requestUpload(
                new RequestUpload(projectId, editor.getId(), "bounce.wav", "audio/wav", SIZE, null, null, null));
        String key = ticket.source().url().getPath().replaceFirst("^/" + SeaweedFsTestConfiguration.BUCKET + "/", "");
        s3.putObject(
                put -> put.bucket(SeaweedFsTestConfiguration.BUCKET).key(key), RequestBody.fromBytes(new byte[SIZE]));

        rabbitmq.getDockerClient().pauseContainerCmd(rabbitmq.getContainerId()).exec();
        UUID versionId = versions.confirm(projectId, ticket.version().id(), editor.getId())
                .id();
        await().atMost(Duration.ofSeconds(30)).until(() -> incompletePublications() > 0);

        rabbitmq.getDockerClient()
                .unpauseContainerCmd(rabbitmq.getContainerId())
                .exec();
        publications.resubmitIncompletePublicationsOlderThan(Duration.ZERO);

        ProcessAudioJob job = rabbit.receiveAndConvert(
                Topology.WORKER_AUDIO_PROCESS_QUEUE, 15_000, new ParameterizedTypeReference<>() {});
        assertThat(job).isNotNull();
        assertThat(job.versionId()).isEqualTo(versionId);
        await().atMost(Duration.ofSeconds(10)).until(() -> incompletePublications() == 0);
    }

    private long incompletePublications() {
        return jdbc.sql("select count(*) from event_publication where completion_date is null")
                .query(Long.class)
                .single();
    }

    private boolean isPaused() {
        return Boolean.TRUE.equals(rabbitmq.getDockerClient()
                .inspectContainerCmd(rabbitmq.getContainerId())
                .exec()
                .getState()
                .getPaused());
    }
}
