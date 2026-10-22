package dev.higor.trackflow.api.shared.error;

import java.util.UUID;

/**
 * The resource does not exist <em>or</em> the caller is not a member of its project. Both cases
 * produce the same 404 so that ids cannot be used to probe for other people's projects.
 */
public final class NotFoundException extends DomainException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }

    /** {@code NotFoundException.of("comment", id)} produces code {@code comment-not-found}. */
    public static NotFoundException of(String resource, UUID id) {
        return new NotFoundException(resource + "-not-found", "%s %s not found".formatted(resource, id));
    }
}
