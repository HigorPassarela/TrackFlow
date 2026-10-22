package dev.higor.trackflow.api.project.web;

import dev.higor.trackflow.api.project.application.CreatedInvite;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Returned once, when the owner creates the invite. {@code acceptPath} is the link to share; it
 * cannot be shown again because only its hash is stored.
 */
public record CreatedInviteResponse(UUID id, String role, Instant expiresAt, String token, String acceptPath) {

    static CreatedInviteResponse from(CreatedInvite created) {
        return new CreatedInviteResponse(
                created.invite().id(),
                created.invite().role().name().toLowerCase(Locale.ROOT),
                created.invite().expiresAt(),
                created.token(),
                "/api/invites/" + created.token() + "/accept");
    }
}
