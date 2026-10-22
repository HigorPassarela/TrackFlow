package dev.higor.trackflow.api.version.domain;

import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
import jakarta.persistence.Entity;
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

/** A public, time-limited link to listen to one ready version without an account. */
@Entity
@Table(name = "share_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class ShareLink {

    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 30;
    public static final int DEFAULT_DAYS = 7;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Version
    private long rowVersion;

    private UUID projectId;

    private UUID versionId;

    // char(64): a SHA-256 hex digest always has exactly 64 characters.
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tokenHash;

    private UUID createdBy;

    private Instant createdAt;

    private Instant expiresAt;

    private @Nullable Instant revokedAt;

    @Builder
    private ShareLink(
            UUID projectId, UUID versionId, String tokenHash, UUID createdBy, int validDays, Instant createdAt) {
        if (validDays < MIN_DAYS || validDays > MAX_DAYS) {
            throw new IllegalArgumentException("Share links last 1 to 30 days");
        }
        this.projectId = projectId;
        this.versionId = versionId;
        this.tokenHash = tokenHash;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = createdAt.plus(Duration.ofDays(validDays));
    }

    /** Expired and revoked links behave exactly like links that never existed. */
    public boolean isUsable(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    /** The owner may revoke any link; anyone else only the links they created. */
    public void revoke(UUID userId, boolean isOwner, Instant at) {
        if (!isOwner && !createdBy.equals(userId)) {
            throw new PermissionDeniedException("Only the owner and the link's creator can revoke it");
        }
        if (revokedAt == null) {
            revokedAt = at;
        }
    }
}
