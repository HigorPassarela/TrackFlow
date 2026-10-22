package dev.higor.trackflow.api.project.web;

import dev.higor.trackflow.api.project.application.ProjectView;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ProjectResponse(
        UUID id,
        String title,
        @Nullable String description,
        @Nullable String genre,
        Owner owner,
        String myRole,
        boolean archived,
        @Nullable Instant archivedAt,
        Instant lastActivityAt,
        Instant createdAt) {

    public record Owner(UUID id, String name) {}

    static ProjectResponse from(ProjectView view) {
        return new ProjectResponse(
                view.id(),
                view.title(),
                view.description(),
                view.genre(),
                new Owner(view.ownerId(), view.ownerName()),
                view.role().name().toLowerCase(Locale.ROOT),
                view.archivedAt() != null,
                view.archivedAt(),
                view.lastActivityAt(),
                view.createdAt());
    }
}
