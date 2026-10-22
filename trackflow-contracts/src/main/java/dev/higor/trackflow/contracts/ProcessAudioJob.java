package dev.higor.trackflow.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * API → worker: process the uploaded file of a version.
 *
 * <p>Contract rules: records only, no Spring, no Lombok. Evolve by adding fields the consumer can
 * ignore; never rename or remove one while an older consumer may still be running.
 *
 * @param messageId unique per delivery attempt from the API, for tracing
 * @param jobId identifies the processing attempt; results carry it back so stale ones are ignored
 */
public record ProcessAudioJob(
        UUID messageId, UUID jobId, UUID versionId, UUID projectId, String sourceKey, Instant occurredAt) {}
