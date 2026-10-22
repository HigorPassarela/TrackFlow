package dev.higor.trackflow.api.comment.application;

import dev.higor.trackflow.api.comment.domain.TimedComment;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What the application layer returns: entity data plus what it needed from other modules (artistic
 * names). The web layer maps it to its own response type.
 *
 * @param body {@code null} for a removed comment kept only so its thread still reads
 * @param resolvedById who marked it resolved; {@code null} while open
 */
public record CommentView(
        UUID id,
        @Nullable Long positionMs,
        @Nullable Long endPositionMs,
        @Nullable String body,
        UUID authorId,
        String authorName,
        Instant createdAt,
        boolean edited,
        boolean removed,
        @Nullable Instant resolvedAt,
        @Nullable UUID resolvedById,
        @Nullable String resolvedByName,
        List<CommentView> replies) {

    static final String UNKNOWN_AUTHOR = "Deleted user";

    static CommentView of(TimedComment comment, Map<UUID, String> displayNames, List<CommentView> replies) {
        UUID resolvedBy = comment.getResolvedBy();
        return new CommentView(
                comment.getId(),
                comment.getPositionMs(),
                comment.getEndPositionMs(),
                comment.isDeleted() ? null : comment.getBody(),
                comment.getAuthorId(),
                displayNames.getOrDefault(comment.getAuthorId(), UNKNOWN_AUTHOR),
                comment.getCreatedAt(),
                comment.isEdited(),
                comment.isDeleted(),
                comment.getResolvedAt(),
                resolvedBy,
                resolvedBy != null ? displayNames.getOrDefault(resolvedBy, UNKNOWN_AUTHOR) : null,
                List.copyOf(replies));
    }
}
