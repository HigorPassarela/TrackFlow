package dev.higor.trackflow.api.version.application;

import java.time.Instant;
import java.util.UUID;

/** The only object carrying the plain token: returned once, to the member who created the link. */
public record CreatedShareLink(UUID id, UUID versionId, Instant expiresAt, String token) {}
