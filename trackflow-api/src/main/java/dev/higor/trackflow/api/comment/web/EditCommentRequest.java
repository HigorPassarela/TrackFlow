package dev.higor.trackflow.api.comment.web;

import dev.higor.trackflow.api.comment.application.EditComment;
import dev.higor.trackflow.api.comment.domain.TimedComment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body of {@code PATCH .../comments/{commentId}}. Only the text changes; positions stay. */
public record EditCommentRequest(
        @NotBlank @Size(max = TimedComment.MAX_BODY_LENGTH) String body) {

    EditComment toCommand(UUID projectId, UUID versionId, UUID commentId, UUID userId) {
        return new EditComment(projectId, versionId, commentId, userId, body);
    }
}
