package dev.higor.trackflow.api.version.application;

import dev.higor.trackflow.api.storage.SignedRequest;
import org.jspecify.annotations.Nullable;

/**
 * Temporary links for one version: preview and peaks for playback, or the original file and the
 * Ableton project for download.
 */
public record MediaLinks(SignedRequest primary, @Nullable SignedRequest secondary) {}
