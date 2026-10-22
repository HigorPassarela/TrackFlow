/**
 * Object storage behind a port: SeaweedFS locally, any S3-compatible service in production and an
 * in-memory fake in tests. Files never pass through the API; it only signs requests.
 *
 * <p>Public API: {@link dev.higor.trackflow.api.storage.ObjectStorage} and its records. The AWS SDK
 * is confined to the {@code adapter} sub-package.
 */
@ApplicationModule(displayName = "Storage")
package dev.higor.trackflow.api.storage;

import org.springframework.modulith.ApplicationModule;
