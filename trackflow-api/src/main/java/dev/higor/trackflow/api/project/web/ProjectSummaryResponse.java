package dev.higor.trackflow.api.project.web;

import dev.higor.trackflow.api.project.domain.ProjectListItem;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ProjectSummaryResponse(
        UUID id, String title, @Nullable String genre, String myRole, boolean archived, Instant lastActivityAt) {

    static ProjectSummaryResponse from(ProjectListItem item) {
        return new ProjectSummaryResponse(
                item.id(),
                item.title(),
                item.genre(),
                item.role().name().toLowerCase(Locale.ROOT),
                item.archivedAt() != null,
                item.lastActivityAt());
    }
}
