package dev.higor.trackflow.api.project.domain;

import dev.higor.trackflow.api.project.ProjectRole;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One row of "my projects": the project plus the caller's role in it. */
public record ProjectListItem(
        UUID id,
        String title,
        @Nullable String genre,
        ProjectRole role,
        @Nullable Instant archivedAt,
        Instant lastActivityAt) {}
