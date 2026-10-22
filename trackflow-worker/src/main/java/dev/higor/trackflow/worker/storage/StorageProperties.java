package dev.higor.trackflow.worker.storage;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Same {@code trackflow.storage.*} keys as the API, so both apps read the same environment.
 *
 * @param endpoint set for S3-compatible storages (SeaweedFS, R2); empty means AWS itself
 */
@ConfigurationProperties("trackflow.storage")
record StorageProperties(@Nullable URI endpoint, String region, String bucket, String accessKey, String secretKey) {}
