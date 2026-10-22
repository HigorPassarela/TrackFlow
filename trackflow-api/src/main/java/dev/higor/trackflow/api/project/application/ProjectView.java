package dev.higor.trackflow.api.project.application;

import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.domain.Project;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A project as one of its members sees it, with the owner's artistic name and the caller's role. */
public record ProjectView(
        UUID id,
        String title,
        @Nullable String description,
        @Nullable String genre,
        UUID ownerId,
        String ownerName,
        ProjectRole role,
        @Nullable Instant archivedAt,
        Instant lastActivityAt,
        Instant createdAt) {

    static final String UNKNOWN_USER = "Deleted user";

    static ProjectView of(Project project, String ownerName, ProjectRole role) {
        return new ProjectView(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getGenre(),
                project.getOwnerId(),
                ownerName,
                role,
                project.getArchivedAt(),
                project.getLastActivityAt(),
                project.getCreatedAt());
    }
}
