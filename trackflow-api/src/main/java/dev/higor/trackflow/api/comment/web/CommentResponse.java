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
 *
 * @param body {@code null} when {@code removed}: the comment was deleted but its replies remain
 * @param resolution who resolved the comment and when; {@code null} while it is open
 */
public record CommentResponse(
        UUID id,
        @Nullable Long positionMs,
        @Nullable Long endPositionMs,
        @Nullable String body,
        Author author,
        Instant createdAt,
        boolean edited,
        boolean removed,
        boolean resolved,
        @Nullable Resolution resolution,
        List<CommentResponse> replies) {

    public record Author(UUID id, String name) {}

    public record Resolution(Author by, Instant at) {}

    static CommentResponse from(CommentView view) {
        return new CommentResponse(
                view.id(),
                view.positionMs(),
                view.endPositionMs(),
                view.body(),
                new Author(view.authorId(), view.authorName()),
                view.createdAt(),
                view.edited(),
                view.removed(),
                view.resolvedAt() != null,
                resolutionOf(view),
                view.replies().stream().map(CommentResponse::from).toList());
    }

    private static @Nullable Resolution resolutionOf(CommentView view) {
        if (view.resolvedAt() == null || view.resolvedById() == null) {
            return null;
        }
        return new Resolution(
                new Author(view.resolvedById(), String.valueOf(view.resolvedByName())), view.resolvedAt());
    }
}
