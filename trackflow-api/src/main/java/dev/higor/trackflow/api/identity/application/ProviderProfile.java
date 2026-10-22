package dev.higor.trackflow.api.identity.application;

import dev.higor.trackflow.api.identity.domain.AuthProvider;
import org.jspecify.annotations.Nullable;

/** What a login provider tells us about the person, already normalized across providers. */
public record ProviderProfile(
        AuthProvider provider,
        String subject,
        @Nullable String email,
        @Nullable String name,
        @Nullable String login,
        @Nullable String avatarUrl) {}
