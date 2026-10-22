package dev.higor.trackflow.worker.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import dev.higor.trackflow.contracts.AbletonAnalyzed;
import dev.higor.trackflow.contracts.AnalyzeAbletonProjectJob;
import dev.higor.trackflow.contracts.Topology;
import dev.higor.trackflow.worker.ableton.AbletonFixtures;
import dev.higor.trackflow.worker.storage.ObjectStorage;
import dev.higor.trackflow.worker.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;

/** The analysis end to end: job on the queue, file from SeaweedFS, result where the API listens. */
@IntegrationTest
class AnalyzeAbletonProjectJobIT {

    private static final long RESULT_TIMEOUT_MS = 30_000;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin admin;

    @Autowired
    private ObjectStorage storage;

    @TempDir
    Path directory;

    @BeforeEach
    void emptyResults() {
        admin.purgeQueue(Topology.API_ABLETON_ANALYZED_QUEUE, false);
    }

    @Test
    @DisplayName("Scenario: tech house project")
    void analyzesAProjectAndPublishesItsSummary() throws Exception {
        AnalyzeAbletonProjectJob job = jobFor(AbletonFixtures.als("live12-tech-house.xml"));

        AbletonAnalyzed result = send(job);

        assertThat(result).isInstanceOfSatisfying(AbletonAnalyzed.Succeeded.class, succeeded -> {
            assertThat(succeeded.tempo()).isEqualTo(126.0);
            assertThat(succeeded.verifiedVersion()).isTrue();
            assertThat(succeeded.tracks())
                    .extracting(AbletonAnalyzed.Track::name)
                    .containsExactly("Kick", "Bass", "Vocal Chop");
            assertThat(succeeded.tracks().get(1).plugins()).containsExactly("Serum");
        });
    }

    @Test
    @DisplayName("Scenario: file with an external reference")
    void reportsAHostileProjectAsInvalidWithoutRetrying() throws Exception {
        AnalyzeAbletonProjectJob job = jobFor(AbletonFixtures.als("external-entity.xml"));

        AbletonAnalyzed result = send(job);

        assertThat(result)
                .isInstanceOfSatisfying(
                        AbletonAnalyzed.Failed.class,
                        failed -> assertThat(failed.reason()).isEqualTo("invalid-project-file"));
    }

    private AnalyzeAbletonProjectJob jobFor(byte[] als) throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String key = "projects/%s/versions/%s/project.als".formatted(projectId, versionId);
        Path file = Files.write(directory.resolve("project.als"), als);
        storage.upload(file, key, "application/octet-stream");
        return new AnalyzeAbletonProjectJob(
                UUID.randomUUID(), UUID.randomUUID(), versionId, projectId, key, Instant.now());
    }

    private AbletonAnalyzed send(AnalyzeAbletonProjectJob job) {
        rabbit.convertAndSend(Topology.JOBS_EXCHANGE, Topology.ABLETON_ANALYZE_ROUTING_KEY, job);
        AbletonAnalyzed result = rabbit.receiveAndConvert(
                Topology.API_ABLETON_ANALYZED_QUEUE, RESULT_TIMEOUT_MS, new ParameterizedTypeReference<>() {});
        assertThat(result).as("result for jobId=" + job.jobId()).isNotNull();
        assertThat(result.jobId()).isEqualTo(job.jobId());
        return result;
    }
}
