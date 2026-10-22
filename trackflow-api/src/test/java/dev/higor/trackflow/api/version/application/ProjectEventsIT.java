package dev.higor.trackflow.api.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.identity.domain.User;
import dev.higor.trackflow.api.support.Fixtures;
import dev.higor.trackflow.api.support.IntegrationTest;
import dev.higor.trackflow.api.version.ReadyMedia;
import dev.higor.trackflow.api.version.TrackVersions;
import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A member keeps the status channel open while the worker's result arrives. The event must reach
 * the open stream within the spec's 2 seconds, and only after the transaction commits.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(Fixtures.class)
class ProjectEventsIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TrackVersions trackVersions;

    @Autowired
    private TrackVersionRepository versions;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private Fixtures fixtures;

    @Test
    void connectedMemberReceivesReadyWithinTwoSeconds() {
        User owner = fixtures.user("sse");
        UUID projectId = fixtures.projectOwnedBy(owner);
        UUID jobId = UUID.randomUUID();
        UUID versionId = processingVersion(projectId, owner, jobId);
        var stream = mvc.get()
                .uri("/api/projects/{projectId}/events", projectId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .with(oauth2Login().attributes(attributes -> {
                    attributes.put(
                            AuthenticatedUser.USER_ID_ATTRIBUTE, owner.getId().toString());
                    attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, owner.getDisplayName());
                }))
                .exchange();

        trackVersions.completeProcessing(
                versionId, jobId, new ReadyMedia("preview.mp3", "peaks.json", 1000L, 120.0, -9.0, -1.0));

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(stream.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("event:version-status")
                        .contains(versionId.toString())
                        .contains("\"status\":\"ready\""));
    }

    /** Puts a version straight into PROCESSING with a known job, as if the worker had picked it up. */
    private UUID processingVersion(UUID projectId, User owner, UUID jobId) {
        return transactions.execute(status -> {
            TrackVersion version = versions.save(TrackVersion.builder()
                    .projectId(projectId)
                    .uploadedBy(owner.getId())
                    .originalFileName("bounce.wav")
                    .contentType("audio/wav")
                    .sizeBytes(10)
                    .createdAt(Instant.now())
                    .build());
            version.assignStorageKeys("projects/%s/versions/%s/source.wav".formatted(projectId, version.getId()), null);
            version.confirm(1, Instant.now());
            version.startProcessing(jobId, Instant.now());
            assertThat(version.getStatus()).isEqualTo(VersionStatus.PROCESSING);
            return version.getId();
        });
    }
}
