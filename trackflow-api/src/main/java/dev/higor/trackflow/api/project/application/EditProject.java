package dev.higor.trackflow.api.project.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Partial update: a {@code null} field keeps its current value; an empty description or genre
 * clears it.
 */
public record EditProject(
        UUID projectId,
        UUID userId,
        @Nullable String title,
        @Nullable String description,
        @Nullable String genre) {}
