package dev.higor.trackflow.api.identity.application;

import dev.higor.trackflow.api.identity.domain.AuthProvider;
import dev.higor.trackflow.api.identity.domain.User;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AccountView(
        UUID id,
        AuthProvider provider,
        String displayName,
        @Nullable String email,
        @Nullable String avatarUrl,
        Instant createdAt) {

    static AccountView of(User user) {
        return new AccountView(
                user.getId(),
                user.getProvider(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getCreatedAt());
    }
}
