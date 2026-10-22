package dev.higor.trackflow.api.version.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Bound from {@code trackflow.version.*}; the values live in {@code application.yml}. */
@ConfigurationProperties("trackflow.version")
public record VersionProperties(Upload upload, Playback playback, PendingCleanup pendingCleanup) {

    public record Upload(DataSize maxFileSize, DataSize maxAbletonFileSize, Duration urlTtl) {}

    public record Playback(Duration urlTtl) {}

    public record PendingCleanup(Duration maxAge) {}
}
