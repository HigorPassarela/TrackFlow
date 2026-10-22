package dev.higor.trackflow.api.version.web;

import dev.higor.trackflow.api.storage.SignedRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A request the client sends straight to the storage, with the headers that are part of the signature. */
public record SignedRequestResponse(URI url, Map<String, String> headers, Instant expiresAt) {

    static SignedRequestResponse from(SignedRequest request) {
        return new SignedRequestResponse(request.url(), request.headers(), request.expiresAt());
    }

    static @Nullable SignedRequestResponse fromNullable(@Nullable SignedRequest request) {
        return request == null ? null : from(request);
    }
}
