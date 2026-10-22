package dev.higor.trackflow.api.version.application;

import dev.higor.trackflow.api.storage.SignedRequest;
import org.jspecify.annotations.Nullable;

/**
 * Everything a visitor with a share link may see, and nothing more: no comments, no other
 * versions, no member list.
 */
public record ListenView(
        String projectTitle,
        @Nullable Integer versionNumber,
        String authorName,
        @Nullable Long durationMs,
        SignedRequest preview,
        SignedRequest peaks) {}
