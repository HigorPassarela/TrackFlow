package dev.higor.trackflow.api.project.web;

import dev.higor.trackflow.api.project.application.MemberView;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record MemberResponse(UUID userId, String displayName, String role, Instant joinedAt) {

    static MemberResponse from(MemberView view) {
        return new MemberResponse(
                view.userId(), view.displayName(), view.role().name().toLowerCase(Locale.ROOT), view.joinedAt());
    }
}
