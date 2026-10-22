package dev.higor.trackflow.api.version.application;

import dev.higor.trackflow.api.storage.SignedRequest;
import org.jspecify.annotations.Nullable;

/** The pending version and where the client must send each file. */
public record UploadTicket(
        VersionView version, SignedRequest source, @Nullable SignedRequest ableton) {}
