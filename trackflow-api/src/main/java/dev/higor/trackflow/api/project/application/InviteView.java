package dev.higor.trackflow.api.project.application;

import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.domain.ProjectInvite;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** An invite as the owner sees it later: never with the link, which only exists at creation. */
public record InviteView(
        UUID id, ProjectRole role, UUID createdBy, String createdByName, Instant createdAt, Instant expiresAt) {

    static InviteView of(ProjectInvite invite, Map<UUID, String> displayNames) {
        return new InviteView(
                invite.getId(),
                invite.getRole(),
                invite.getCreatedBy(),
                displayNames.getOrDefault(invite.getCreatedBy(), ProjectView.UNKNOWN_USER),
                invite.getCreatedAt(),
                invite.getExpiresAt());
    }
}
