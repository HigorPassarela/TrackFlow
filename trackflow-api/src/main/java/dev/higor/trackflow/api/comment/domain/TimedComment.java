package dev.higor.trackflow.api.comment.domain;

import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
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
 * A comment pinned to a version, or a reply inside a comment's thread.
 *
 * <p>Threads are one level deep: a reply always points to the root comment, never to another
 * reply. Only root comments have a position and can be resolved.
 */
@Entity
@Table(name = "timed_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class TimedComment {

    public static final int MAX_BODY_LENGTH = 2000;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Version
    private long rowVersion;

    private UUID projectId;

    private UUID versionId;

    private @Nullable UUID parentId;

    private UUID authorId;

    private @Nullable Long positionMs;

    private @Nullable Long endPositionMs;

    private String body;

    private Instant createdAt;

    private @Nullable Instant resolvedAt;

    private @Nullable UUID resolvedBy;

    private @Nullable Instant editedAt;

    /** Soft delete: a removed comment with replies keeps its thread (see {@link #visibility}). */
    private @Nullable Instant deletedAt;

    /**
     * The builder is the only way to create a comment, and it always runs this validation.
     *
     * @param range required for root comments, forbidden for replies
     */
    @Builder
    private TimedComment(
            UUID projectId,
            UUID versionId,
            @Nullable UUID parentId,
            UUID authorId,
            @Nullable TimeRange range,
            String body,
            Instant createdAt) {
        boolean isReply = parentId != null;
        if (isReply && range != null) {
            throw new IllegalArgumentException("Replies have no position of their own");
        }
        if (!isReply && range == null) {
            throw new IllegalArgumentException("Root comments need a position");
        }
        this.projectId = projectId;
        this.versionId = versionId;
        this.parentId = parentId;
        this.authorId = authorId;
        this.positionMs = range != null ? range.startMs() : null;
        this.endPositionMs = range != null ? range.endMs() : null;
        this.body = requireValidBody(body);
        this.createdAt = createdAt;
    }

    public boolean isReply() {
        return parentId != null;
    }

    public boolean isResolved() {
        return resolvedAt != null;
    }

    /** The id of the thread this comment belongs to: itself for a root, the parent for a reply. */
    public UUID threadId() {
        return parentId != null ? parentId : id;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isEdited() {
        return editedAt != null;
    }

    /** Only the author changes the text, not even the owner: it is their words. */
    public void edit(UUID userId, String newBody, Instant at) {
        requireNotDeleted();
        if (!authorId.equals(userId)) {
            throw new PermissionDeniedException("Only the author can edit a comment");
        }
        body = requireValidBody(newBody);
        editedAt = at;
    }

    /** The author or the project owner. Always soft: whether it still shows depends on its replies. */
    public void delete(UUID userId, ProjectRole role, Instant at) {
        if (!authorId.equals(userId) && role != ProjectRole.OWNER) {
            throw new PermissionDeniedException("Only the author and the owner can delete a comment");
        }
        if (deletedAt == null) {
            deletedAt = at;
        }
    }

    /**
     * How the comment shows in its version's list. A deleted comment without visible replies
     * disappears; with replies it stays as a "removed" placeholder so the thread still reads.
     */
    public Visibility visibility(boolean hasVisibleReplies) {
        if (deletedAt == null) {
            return Visibility.SHOWN;
        }
        return hasVisibleReplies && !isReply() ? Visibility.REMOVED_PLACEHOLDER : Visibility.HIDDEN;
    }

    public enum Visibility {
        SHOWN,
        REMOVED_PLACEHOLDER,
        HIDDEN
    }

    /** Idempotent: resolving an already resolved comment keeps who resolved it first. */
    public void resolve(UUID userId, ProjectRole role, Instant at) {
        requireCanChangeResolution(userId, role);
        if (resolvedAt == null) {
            resolvedAt = at;
            resolvedBy = userId;
        }
    }

    public void reopen(UUID userId, ProjectRole role) {
        requireCanChangeResolution(userId, role);
        resolvedAt = null;
        resolvedBy = null;
    }

    private void requireNotDeleted() {
        if (deletedAt != null) {
            throw new BusinessRuleException("comment-deleted", "The comment was deleted");
        }
    }

    private void requireCanChangeResolution(UUID userId, ProjectRole role) {
        requireNotDeleted();
        if (isReply()) {
            throw new BusinessRuleException("reply-cannot-be-resolved", "Only root comments can be resolved");
        }
        boolean isAuthor = authorId.equals(userId);
        if (!isAuthor && !role.isAtLeast(ProjectRole.EDITOR)) {
            throw new PermissionDeniedException("Only the author, editors and the owner can resolve a comment");
        }
    }

    private static String requireValidBody(String body) {
        String stripped = body.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("Comment body must have 1 to " + MAX_BODY_LENGTH + " characters");
        }
        return stripped;
    }
}
