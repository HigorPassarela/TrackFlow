package dev.higor.trackflow.api.project.domain;

import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * A single-use link that adds whoever accepts it to the project with a fixed role. The entity holds
 * only the hash of the token, so the link cannot be recovered from the database.
 */
@Entity
@Table(name = "project_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class ProjectInvite {

    public static final Duration VALIDITY = Duration.ofDays(7);

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Version
    private long rowVersion;

    private UUID projectId;

    // char(64): a SHA-256 hex digest always has exactly 64 characters.
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    private ProjectRole role;

    private UUID createdBy;

    private Instant createdAt;

    private Instant expiresAt;

    private @Nullable UUID acceptedBy;

    private @Nullable Instant acceptedAt;

    private @Nullable Instant revokedAt;

    @Builder
    private ProjectInvite(UUID projectId, String tokenHash, ProjectRole role, UUID createdBy, Instant createdAt) {
        if (role == ProjectRole.OWNER) {
            throw new BusinessRuleException("single-owner", "Invites are for editors and listeners only");
        }
        this.projectId = projectId;
        this.tokenHash = tokenHash;
        this.role = role;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = createdAt.plus(VALIDITY);
    }

    public boolean isPending(Instant now) {
        return acceptedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    /** Checks used and revoked before expiry: a consumed link says so even after it would have expired. */
    public void accept(UUID userId, Instant now) {
        if (acceptedAt != null || revokedAt != null) {
            throw new BusinessRuleException("invite-not-available", "This invite is no longer available");
        }
        if (!now.isBefore(expiresAt)) {
            throw new BusinessRuleException("invite-expired", "This invite has expired");
        }
        acceptedBy = userId;
        acceptedAt = now;
    }

    /** Idempotent for an already revoked invite; an accepted invite cannot be revoked any more. */
    public void revoke(Instant now) {
        if (acceptedAt != null) {
            throw new BusinessRuleException("invite-not-available", "This invite was already used");
        }
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
