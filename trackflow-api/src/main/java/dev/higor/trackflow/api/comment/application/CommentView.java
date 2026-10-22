package dev.higor.trackflow.api.comment.application;

import dev.higor.trackflow.api.comment.domain.TimedComment;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What the application layer returns: entity data plus what it needed from other modules (the
 * author's artistic name). The web layer maps it to its own response type.
 */
public record CommentView(
        UUID id,
        @Nullable Long positionMs,
        @Nullable Long endPositionMs,
        String body,
        UUID authorId,
        String authorName,
        Instant createdAt,
        @Nullable Instant resolvedAt,
        List<CommentView> replies) {

    static final String UNKNOWN_AUTHOR = "Deleted user";

    static CommentView of(TimedComment comment, Map<UUID, String> displayNames, List<CommentView> replies) {
        return new CommentView(
                comment.getId(),
                comment.getPositionMs(),
                comment.getEndPositionMs(),
                comment.getBody(),
                comment.getAuthorId(),
                displayNames.getOrDefault(comment.getAuthorId(), UNKNOWN_AUTHOR),
                comment.getCreatedAt(),
                comment.getResolvedAt(),
                List.copyOf(replies));
    }
}
