package dev.higor.trackflow.api.storage.adapter;

import dev.higor.trackflow.api.storage.ObjectStorage;
import dev.higor.trackflow.api.storage.SignedRequest;
import dev.higor.trackflow.api.storage.StoredObject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.presigner.PresignedRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Adapter of {@link ObjectStorage} for any S3-compatible storage. Tested against SeaweedFS. */
@Component
class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    S3ObjectStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public SignedRequest presignUpload(String key, String contentType, long sizeBytes, Duration ttl) {
        // Content type and length are part of the signature: the client cannot upload anything else.
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        return toSignedRequest(presigner.presignPutObject(
                presign -> presign.signatureDuration(ttl).putObjectRequest(put)));
    }

    @Override
    public SignedRequest presignDownload(String key, @Nullable String downloadFileName, Duration ttl) {
        GetObjectRequest.Builder get =
                GetObjectRequest.builder().bucket(properties.bucket()).key(key);
        if (downloadFileName != null) {
            get.responseContentDisposition(ContentDisposition.attachment()
                    .filename(downloadFileName, StandardCharsets.UTF_8)
                    .build()
                    .toString());
        }
        GetObjectRequest request = get.build();
        return toSignedRequest(presigner.presignGetObject(
                presign -> presign.signatureDuration(ttl).getObjectRequest(request)));
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse response =
                    s3.headObject(request -> request.bucket(properties.bucket()).key(key));
            return Optional.of(new StoredObject(key, response.contentLength(), response.contentType()));
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(request -> request.bucket(properties.bucket()).key(key));
    }

    private static SignedRequest toSignedRequest(PresignedRequest presigned) {
        // The browser sets Host itself; every other signed header must be sent as is.
        Map<String, String> headers = presigned.signedHeaders().entrySet().stream()
                .filter(header -> !header.getKey().equalsIgnoreCase("host"))
                .collect(
                        Collectors.toUnmodifiableMap(Map.Entry::getKey, header -> String.join(",", header.getValue())));
        return new SignedRequest(URI.create(presigned.url().toString()), headers, presigned.expiration());
    }
}
