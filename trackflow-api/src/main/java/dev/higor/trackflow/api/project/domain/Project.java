package dev.higor.trackflow.api.project.domain;

import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import jakarta.persistence.Entity;
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
 * A track being produced. Archiving makes it read-only: writes need an active project, checked by
 * {@code ProjectAccess.requireRoleOnActiveProject} and again by this entity.
 */
@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class Project {

    public static final int MAX_TITLE_LENGTH = 120;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_GENRE_LENGTH = 60;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Version
    private long rowVersion;

    private String title;

    private @Nullable String description;

    private @Nullable String genre;

    private UUID ownerId;

    private @Nullable Instant archivedAt;

    private Instant lastActivityAt;

    private Instant createdAt;

    private Instant updatedAt;

    @Builder
    private Project(
            String title, @Nullable String description, @Nullable String genre, UUID ownerId, Instant createdAt) {
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.lastActivityAt = createdAt;
        applyDetails(title, description, genre);
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** Editing counts as activity, so the project moves to the top of its members' lists. */
    public void edit(String title, @Nullable String description, @Nullable String genre, Instant at) {
        requireActive();
        applyDetails(title, description, genre);
        updatedAt = at;
        lastActivityAt = at;
    }

    public void archive(Instant at) {
        requireActive();
        archivedAt = at;
        updatedAt = at;
    }

    public void unarchive(Instant at) {
        if (archivedAt == null) {
            throw new BusinessRuleException("project-not-archived", "The project is not archived");
        }
        archivedAt = null;
        updatedAt = at;
    }

    public void requireActive() {
        if (archivedAt != null) {
            throw new BusinessRuleException("project-archived", "The project is archived and read-only");
        }
    }

    private void applyDetails(String title, @Nullable String description, @Nullable String genre) {
        this.title = requireLength(title.strip(), MAX_TITLE_LENGTH, "title");
        this.description = blankToNull(description, MAX_DESCRIPTION_LENGTH, "description");
        this.genre = blankToNull(genre, MAX_GENRE_LENGTH, "genre");
    }

    private static @Nullable String blankToNull(@Nullable String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireLength(value.strip(), max, field);
    }

    private static String requireLength(String value, int max, String field) {
        if (value.isEmpty() || value.length() > max) {
            throw new IllegalArgumentException("Project %s must have 1 to %d characters".formatted(field, max));
        }
        return value;
    }
}
