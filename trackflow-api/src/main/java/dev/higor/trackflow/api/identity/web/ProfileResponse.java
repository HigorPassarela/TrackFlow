package dev.higor.trackflow.api.identity.web;

import dev.higor.trackflow.api.identity.application.AccountView;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ProfileResponse(
        UUID id,
        String provider,
        String displayName,
        @Nullable String email,
        @Nullable String avatarUrl,
        Instant createdAt) {

    static ProfileResponse from(AccountView view) {
        return new ProfileResponse(
                view.id(),
                view.provider().name().toLowerCase(Locale.ROOT),
                view.displayName(),
                view.email(),
                view.avatarUrl(),
                view.createdAt());
    }
}
