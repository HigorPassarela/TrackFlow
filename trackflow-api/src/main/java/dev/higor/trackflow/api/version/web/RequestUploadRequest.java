package dev.higor.trackflow.api.version.web;

import dev.higor.trackflow.api.version.application.RequestUpload;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code POST .../versions/uploads}. Shape and presence are checked here; format and size
 * limits come from configuration and are checked by the service.
 */
public record RequestUploadRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 100) String contentType,
        @Positive long sizeBytes,
        @Nullable @Valid AbletonFile ableton,
        @Nullable @Size(max = TrackVersion.MAX_TITLE_LENGTH) String title,
        @Nullable @Size(max = TrackVersion.MAX_NOTES_LENGTH) String notes) {

    public record AbletonFile(
            @NotBlank @Size(max = 255) String fileName,
            @Positive long sizeBytes) {}

    RequestUpload toCommand(UUID projectId, UUID userId) {
        return new RequestUpload(
                projectId,
                userId,
                fileName,
                contentType,
                sizeBytes,
                ableton == null ? null : new RequestUpload.AbletonAttachment(ableton.fileName(), ableton.sizeBytes()),
                title,
                notes);
    }
}
