package dev.higor.trackflow.api.project.web;

import dev.higor.trackflow.api.project.application.InviteView;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** A pending invite in listings: role, creator and expiry, never the link. */
public record InviteResponse(UUID id, String role, Creator createdBy, Instant createdAt, Instant expiresAt) {

    public record Creator(UUID id, String name) {}

    static InviteResponse from(InviteView view) {
        return new InviteResponse(
                view.id(),
                view.role().name().toLowerCase(Locale.ROOT),
                new Creator(view.createdBy(), view.createdByName()),
                view.createdAt(),
                view.expiresAt());
    }
}
