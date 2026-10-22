package dev.higor.trackflow.api.version.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Partial update: {@code null} keeps the current value; an empty title or notes clears it. */
public record EditVersion(
        UUID projectId,
        UUID versionId,
        UUID userId,
        @Nullable String title,
        @Nullable String notes,
        @Nullable Double bpmOverride) {}
