package dev.higor.trackflow.api.version.web;

import dev.higor.trackflow.api.version.application.EditVersion;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Body of {@code PATCH .../versions/{versionId}}: absent fields stay as they are. */
public record UpdateVersionRequest(
        @Nullable @Size(max = TrackVersion.MAX_TITLE_LENGTH) String title,
        @Nullable @Size(max = TrackVersion.MAX_NOTES_LENGTH) String notes,
        @Nullable @DecimalMin("40") @DecimalMax("250") Double bpm) {

    EditVersion toCommand(UUID projectId, UUID versionId, UUID userId) {
        return new EditVersion(projectId, versionId, userId, title, notes, bpm);
    }
}
