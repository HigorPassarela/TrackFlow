package dev.higor.trackflow.api.storage;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Fake of the {@link ObjectStorage} port for service tests. Prefer it over a Mockito mock when the
 * test is about a flow (request upload, simulate the client, confirm): the fake keeps state, so
 * the test reads like the real sequence.
 */
public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryObjectStorage(Clock clock) {
        this.clock = clock;
    }

    @Override
    public SignedRequest presignUpload(String key, String contentType, long sizeBytes, Duration ttl) {
        return new SignedRequest(
                URI.create("https://storage.test/" + key),
                Map.of("Content-Type", contentType, "Content-Length", String.valueOf(sizeBytes)),
                clock.instant().plus(ttl));
    }

    /** The file name goes into the URL so tests can see which name the browser would save. */
    @Override
    public SignedRequest presignDownload(String key, @Nullable String downloadFileName, Duration ttl) {
        String query = downloadFileName == null
                ? ""
                : "?download="
                        + URLEncoder.encode(downloadFileName, StandardCharsets.UTF_8)
                                .replace("+", "%20");
        return new SignedRequest(
                URI.create("https://storage.test/" + key + query),
                Map.of(),
                clock.instant().plus(ttl));
    }

    @Override
    public Optional<StoredObject> head(String key) {
        return Optional.ofNullable(objects.get(key));
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    /** What the client does between "request upload" and "confirm". */
    public void simulateUpload(String key, long sizeBytes, String contentType) {
        objects.put(key, new StoredObject(key, sizeBytes, contentType));
    }

    public boolean contains(String key) {
        return objects.containsKey(key);
    }
}
