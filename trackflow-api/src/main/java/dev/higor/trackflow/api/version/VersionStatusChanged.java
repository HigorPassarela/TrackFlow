package dev.higor.trackflow.api.version;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on every status transition of a confirmed version, inside the transaction that made
 * it. Listeners that talk to the outside world must run after commit, so a rolled-back transition
 * is never announced.
 */
public record VersionStatusChanged(UUID versionId, UUID projectId, VersionStatus status, Instant occurredAt) {}
