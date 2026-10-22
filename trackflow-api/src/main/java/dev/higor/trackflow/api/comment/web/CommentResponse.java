package dev.higor.trackflow.api.comment.web;

import dev.higor.trackflow.api.comment.application.CommentView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * API contract for a comment. Kept separate from {@link CommentView} on purpose: the HTTP shape
 * can change (nesting the author, exposing {@code resolved} as a boolean) without touching the
 * use cases.
 */
public record CommentResponse(
        UUID id,
        @Nullable Long positionMs,
        @Nullable Long endPositionMs,
        String body,
        Author author,
        Instant createdAt,
        boolean resolved,
        List<CommentResponse> replies) {

    public record Author(UUID id, String name) {}

    static CommentResponse from(CommentView view) {
        return new CommentResponse(
                view.id(),
                view.positionMs(),
                view.endPositionMs(),
                view.body(),
                new Author(view.authorId(), view.authorName()),
                view.createdAt(),
                view.resolvedAt() != null,
                view.replies().stream().map(CommentResponse::from).toList());
    }
}
