package dev.higor.trackflow.worker.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** Adapter of {@link ObjectStorage} for any S3-compatible storage. Tested against SeaweedFS. */
@Component
class S3ObjectStorage implements ObjectStorage {

    // Path-style URLs (endpoint/bucket/key) because SeaweedFS has no wildcard DNS for buckets.
    private static final S3Configuration PATH_STYLE =
            S3Configuration.builder().pathStyleAccessEnabled(true).build();

    private final S3Client s3;
    private final String bucket;

    S3ObjectStorage(StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .serviceConfiguration(PATH_STYLE);
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        this.s3 = builder.build();
        this.bucket = properties.bucket();
    }

    @Override
    public void download(String key, Path target) {
        try {
            // The SDK refuses to overwrite, and a retry may find the file of the previous attempt.
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not replace " + target, e);
        }
        s3.getObject(request -> request.bucket(bucket).key(key), target);
    }

    @Override
    public void upload(Path source, String key, String contentType) {
        s3.putObject(request -> request.bucket(bucket).key(key).contentType(contentType), RequestBody.fromFile(source));
    }
}
