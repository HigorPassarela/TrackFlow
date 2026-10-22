package dev.higor.trackflow.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * API → worker: read the Ableton Live project attached to a version.
 *
 * @param jobId identifies the analysis attempt; the result carries it back so stale ones are ignored
 */
public record AnalyzeAbletonProjectJob(
        UUID messageId, UUID jobId, UUID versionId, UUID projectId, String alsKey, Instant occurredAt) {}
