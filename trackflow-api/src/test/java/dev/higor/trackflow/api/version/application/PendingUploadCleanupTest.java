package dev.higor.trackflow.api.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import dev.higor.trackflow.api.storage.InMemoryObjectStorage;
import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class PendingUploadCleanupTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private TrackVersionRepository versions;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryObjectStorage storage = new InMemoryObjectStorage(clock);
    private PendingUploadCleanup cleanup;

    @BeforeEach
    void setUp() {
        VersionProperties properties = new VersionProperties(
                new VersionProperties.Upload(DataSize.ofGigabytes(1), DataSize.ofMegabytes(50), Duration.ofMinutes(15)),
                new VersionProperties.Playback(Duration.ofHours(1)),
                new VersionProperties.PendingCleanup(Duration.ofHours(24)));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        cleanup = new PendingUploadCleanup(
                versions, storage, new TransactionTemplate(transactionManager), properties, clock);
    }

    @Test
    @DisplayName("Limpeza de envio abandonado: versão aguardando há 25 horas some junto com os arquivos")
    void discardsUploadsOlderThanOneDayWithTheirObjects() {
        TrackVersion abandoned = pending(NOW.minus(Duration.ofHours(25)), true);
        storage.simulateUpload(abandoned.getSourceKey(), 10, "audio/wav");
        storage.simulateUpload(abandoned.getAlsKey(), 5, "application/octet-stream");
        given(versions.findByStatusAndCreatedAtBefore(VersionStatus.PENDING_UPLOAD, NOW.minus(Duration.ofHours(24))))
                .willReturn(List.of(abandoned));
        given(versions.deleteIfStillPending(abandoned.getId())).willReturn(1);

        cleanup.discardAbandonedUploads();

        then(versions).should().deleteIfStillPending(abandoned.getId());
        assertThat(storage.contains(abandoned.getSourceKey())).isFalse();
        assertThat(storage.contains(abandoned.getAlsKey())).isFalse();
    }

    @Test
    void asksOnlyForUploadsOlderThanTheConfiguredAge() {
        given(versions.findByStatusAndCreatedAtBefore(any(), any())).willReturn(List.of());

        cleanup.discardAbandonedUploads();

        then(versions)
                .should()
                .findByStatusAndCreatedAtBefore(VersionStatus.PENDING_UPLOAD, Instant.parse("2026-10-21T10:00:00Z"));
    }

    @Test
    void keepsObjectsWhenTheVersionWasConfirmedMeanwhile() {
        TrackVersion raced = pending(NOW.minus(Duration.ofHours(30)), false);
        storage.simulateUpload(raced.getSourceKey(), 10, "audio/wav");
        given(versions.findByStatusAndCreatedAtBefore(any(), any())).willReturn(List.of(raced));
        given(versions.deleteIfStillPending(raced.getId())).willReturn(0);

        cleanup.discardAbandonedUploads();

        assertThat(storage.contains(raced.getSourceKey())).isTrue();
    }

    @Test
    void doesNothingWithoutAbandonedUploads() {
        given(versions.findByStatusAndCreatedAtBefore(any(), any())).willReturn(List.of());

        cleanup.discardAbandonedUploads();

        then(versions).should(never()).deleteIfStillPending(any());
    }

    private static TrackVersion pending(Instant createdAt, boolean withAbleton) {
        TrackVersion version = TrackVersion.builder()
                .projectId(PROJECT)
                .uploadedBy(UUID.randomUUID())
                .originalFileName("bounce.wav")
                .contentType("audio/wav")
                .sizeBytes(10)
                .alsFileName(withAbleton ? "ibiza.als" : null)
                .alsSizeBytes(withAbleton ? 5L : null)
                .createdAt(createdAt)
                .build();
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(version, "id", id);
        String prefix = "projects/%s/versions/%s/".formatted(PROJECT, id);
        version.assignStorageKeys(prefix + "source.wav", withAbleton ? prefix + "project.als" : null);
        return version;
    }
}
