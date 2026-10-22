package dev.higor.trackflow.worker.storage;

import java.nio.file.Path;

/**
 * The worker's side of the storage: it downloads what the client uploaded and uploads what it
 * produced. Keys are the deterministic ones from design D4, so a retry overwrites instead of
 * duplicating.
 */
public interface ObjectStorage {

    /** Writes the object to {@code target}, replacing it if it exists. */
    void download(String key, Path target);

    void upload(Path source, String key, String contentType);
}
