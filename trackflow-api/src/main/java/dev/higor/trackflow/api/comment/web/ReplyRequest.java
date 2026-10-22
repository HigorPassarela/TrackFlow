package dev.higor.trackflow.api.comment.web;

import dev.higor.trackflow.api.comment.application.ReplyToComment;
import dev.higor.trackflow.api.comment.domain.TimedComment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body of {@code POST .../comments/{commentId}/replies}. */
public record ReplyRequest(
        @NotBlank @Size(max = TimedComment.MAX_BODY_LENGTH) String body) {

    ReplyToComment toCommand(UUID projectId, UUID versionId, UUID commentId, UUID authorId) {
        return new ReplyToComment(projectId, versionId, commentId, authorId, body);
    }
}
