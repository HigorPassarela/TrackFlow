package dev.higor.trackflow.api.project.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CreateProject(
        UUID ownerId,
        String title,
        @Nullable String description,
        @Nullable String genre) {}
