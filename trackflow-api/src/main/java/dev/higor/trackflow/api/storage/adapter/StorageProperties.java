package dev.higor.trackflow.api.storage.adapter;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code trackflow.storage.*}. Defaults live in {@code application.yml}, never in code.
 *
 * @param endpoint set for S3-compatible storages (SeaweedFS, R2); empty means AWS itself
 */
@ConfigurationProperties("trackflow.storage")
record StorageProperties(@Nullable URI endpoint, String region, String bucket, String accessKey, String secretKey) {}
