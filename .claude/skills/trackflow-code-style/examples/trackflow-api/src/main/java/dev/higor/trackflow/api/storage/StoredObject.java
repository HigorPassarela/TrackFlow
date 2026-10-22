package dev.higor.trackflow.api.storage;

import org.jspecify.annotations.Nullable;

public record StoredObject(String key, long sizeBytes, @Nullable String contentType) {}
