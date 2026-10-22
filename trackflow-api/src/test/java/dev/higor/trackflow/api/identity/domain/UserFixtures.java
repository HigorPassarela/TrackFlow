package dev.higor.trackflow.api.identity.domain;

import java.time.Instant;

public final class UserFixtures {

    public static final Instant CREATED_AT = Instant.parse("2026-10-21T22:00:00Z");

    private UserFixtures() {}

    public static User githubUser(String subject) {
        return user(AuthProvider.GITHUB, subject);
    }

    public static User user(AuthProvider provider, String subject) {
        return User.builder()
                .provider(provider)
                .providerSubject(subject)
                .email("produtor@example.com")
                .displayName("Produtor " + subject)
                .createdAt(CREATED_AT)
                .build();
    }
}
