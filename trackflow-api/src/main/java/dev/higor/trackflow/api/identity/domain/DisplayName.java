package dev.higor.trackflow.api.identity.domain;

import java.util.UUID;

/** Projection used to build responses that show who did what. */
public record DisplayName(UUID userId, String displayName) {}
