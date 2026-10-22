package dev.higor.trackflow.api.version.domain;

import dev.higor.trackflow.api.version.ReadyMedia;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

public final class TrackVersionFixtures {

    public static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID UPLOADER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    public static final Instant CREATED = Instant.parse("2026-10-21T22:00:00Z");
    public static final ReadyMedia MEDIA = new ReadyMedia(
            "projects/p/versions/v/preview.mp3", "projects/p/versions/v/peaks.json", 360_000L, 124.0, -9.4, 0.8);

    private TrackVersionFixtures() {}

    /** A pending WAV upload with id and keys, as the service leaves it after the first save. */
    public static TrackVersion pendingWav() {
        TrackVersion version = TrackVersion.builder()
                .projectId(PROJECT)
                .uploadedBy(UPLOADER)
                .originalFileName("bounce v4.wav")
                .contentType("audio/wav")
                .sizeBytes(120L * 1024 * 1024)
                .createdAt(CREATED)
                .build();
        ReflectionTestUtils.setField(version, "id", VERSION);
        version.assignStorageKeys("projects/p/versions/v/source.wav", null);
        return version;
    }

    public static TrackVersion uploaded(int number) {
        TrackVersion version = pendingWav();
        version.confirm(number, CREATED.plusSeconds(60));
        return version;
    }

    public static TrackVersion processing(UUID jobId) {
        TrackVersion version = uploaded(1);
        version.startProcessing(jobId, CREATED.plusSeconds(61));
        return version;
    }

    public static TrackVersion ready() {
        UUID jobId = UUID.randomUUID();
        TrackVersion version = processing(jobId);
        version.complete(jobId, MEDIA, CREATED.plusSeconds(90));
        return version;
    }

    public static TrackVersion failed() {
        UUID jobId = UUID.randomUUID();
        TrackVersion version = processing(jobId);
        version.fail(jobId, "processing-error", CREATED.plusSeconds(90));
        return version;
    }
}
