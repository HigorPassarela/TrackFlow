package dev.higor.trackflow.api.storage.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.higor.trackflow.api.storage.ObjectStorage;
import dev.higor.trackflow.api.storage.SignedRequest;
import dev.higor.trackflow.api.support.IntegrationTest;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Plays the browser: uploads through the signed URL exactly as the demo page does. */
@IntegrationTest
class S3ObjectStorageIT {

    private static final String WAV = "audio/wav";

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private ObjectStorage storage;

    @Test
    void uploadsThroughSignedUrlAndReportsTheSizeOnHead() throws Exception {
        byte[] content = new byte[64 * 1024];
        Arrays.fill(content, (byte) 7);
        String key = "projects/it/versions/upload/source.wav";
        SignedRequest upload = storage.presignUpload(key, WAV, content.length, Duration.ofMinutes(15));

        HttpResponse<Void> response = http.send(put(upload, content), HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(storage.head(key)).hasValueSatisfying(stored -> {
            assertThat(stored.sizeBytes()).isEqualTo(content.length);
            assertThat(stored.contentType()).isEqualTo(WAV);
        });
    }

    @Test
    void refusesUploadWithAnotherSizeThanTheSignedOne() throws Exception {
        String key = "projects/it/versions/tampered/source.wav";
        SignedRequest upload = storage.presignUpload(key, WAV, 10, Duration.ofMinutes(15));

        HttpResponse<Void> response = http.send(put(upload, new byte[20]), HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(storage.head(key)).isEmpty();
    }

    @Test
    void reportsMissingObjectAsEmptyAndDeletesQuietly() {
        String key = "projects/it/versions/missing/source.wav";

        storage.delete(key);

        assertThat(storage.head(key)).isEmpty();
    }

    @Test
    void signsDownloadsThatServeTheStoredBytes() throws Exception {
        byte[] content = "RIFF fake wave".getBytes();
        String key = "projects/it/versions/download/source.wav";
        http.send(
                put(storage.presignUpload(key, WAV, content.length, Duration.ofMinutes(15)), content),
                HttpResponse.BodyHandlers.discarding());
        SignedRequest download = storage.presignDownload(key, "bounce final.wav", Duration.ofHours(1));

        HttpResponse<byte[]> response = http.send(
                HttpRequest.newBuilder(download.url()).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValueSatisfying(value -> assertThat(value).contains("attachment"));
    }

    /** Signed headers go out unchanged; Content-Length is set by the HTTP client from the body. */
    private static HttpRequest put(SignedRequest signed, byte[] body) {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(signed.url()).PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        signed.headers().forEach((name, value) -> {
            if (!name.equalsIgnoreCase("content-length")) {
                request.header(name, value);
            }
        });
        return request.build();
    }
}
