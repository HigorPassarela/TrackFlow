package dev.higor.trackflow.api.storage;

import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Port for the object storage. Lives in the module root because {@code version} uses it.
 *
 * <p>This is one of the few ports in the API: the implementation changes between environments
 * (SeaweedFS locally, S3 in production) and tests need an in-memory fake. Files never pass through
 * the API; it only signs requests that clients send straight to the storage.
 */
public interface ObjectStorage {

    /** Signs a PUT that only accepts exactly this content type and size. */
    SignedRequest presignUpload(String key, String contentType, long sizeBytes, Duration ttl);

    /**
     * Signs a GET. With a file name the browser downloads it under that name; without one, it is
     * streamed inline (the storage answers HTTP Range requests, so seeking works).
     */
    SignedRequest presignDownload(String key, @Nullable String downloadFileName, Duration ttl);

    /** Metadata of an existing object, or empty when nothing was uploaded under the key. */
    Optional<StoredObject> head(String key);

    /** Deleting a missing key is not an error. */
    void delete(String key);
}
