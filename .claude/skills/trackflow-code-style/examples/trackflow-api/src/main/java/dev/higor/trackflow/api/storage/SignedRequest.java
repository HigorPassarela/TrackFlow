package dev.higor.trackflow.api.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * A request the client can send directly to the storage until {@code expiresAt}.
 *
 * @param headers headers the client must send unchanged, because they are part of the signature
 */
public record SignedRequest(URI url, Map<String, String> headers, Instant expiresAt) {

    public SignedRequest {
        headers = Map.copyOf(headers);
    }
}
