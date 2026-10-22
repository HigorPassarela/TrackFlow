package dev.higor.trackflow.api.identity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * An account, identified by the provider and the provider's own id for the person. The e-mail is
 * informational only: two providers with the same e-mail are two accounts, never merged.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class User {

    public static final int MIN_DISPLAY_NAME_LENGTH = 2;
    public static final int MAX_DISPLAY_NAME_LENGTH = 60;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Version
    private long rowVersion;

    @Enumerated(EnumType.STRING)
    private AuthProvider provider;

    private String providerSubject;

    private @Nullable String email;

    private String displayName;

    private @Nullable String avatarUrl;

    private Instant createdAt;

    @Builder
    private User(
            AuthProvider provider,
            String providerSubject,
            @Nullable String email,
            String displayName,
            @Nullable String avatarUrl,
            Instant createdAt) {
        if (providerSubject.isBlank()) {
            throw new IllegalArgumentException("Provider subject is required");
        }
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.email = email;
        this.displayName = requireValidDisplayName(displayName);
        this.avatarUrl = avatarUrl;
        this.createdAt = createdAt;
    }

    public void rename(String newDisplayName) {
        displayName = requireValidDisplayName(newDisplayName);
    }

    private static String requireValidDisplayName(String displayName) {
        String stripped = displayName.strip();
        if (stripped.length() < MIN_DISPLAY_NAME_LENGTH || stripped.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("Display name must have %d to %d characters"
                    .formatted(MIN_DISPLAY_NAME_LENGTH, MAX_DISPLAY_NAME_LENGTH));
        }
        return stripped;
    }
}
