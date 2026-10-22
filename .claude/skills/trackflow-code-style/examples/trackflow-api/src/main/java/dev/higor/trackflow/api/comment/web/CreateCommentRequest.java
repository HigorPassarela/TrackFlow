package dev.higor.trackflow.api.comment.web;

import dev.higor.trackflow.api.comment.application.AddComment;
import dev.higor.trackflow.api.comment.domain.TimeRange;
import dev.higor.trackflow.api.comment.domain.TimedComment;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code POST .../comments}. Validation that only needs the request lives here; rules that
 * need state (the version's duration) live in the domain.
 */
public record CreateCommentRequest(
        @NotNull @PositiveOrZero Long positionMs,
        @Nullable @Positive Long endPositionMs,
        @NotBlank @Size(max = TimedComment.MAX_BODY_LENGTH) String body) {

    /** Reported by Bean Validation as field {@code rangeValid}. */
    @AssertTrue(message = "endPositionMs must be after positionMs")
    public boolean isRangeValid() {
        return positionMs == null || endPositionMs == null || endPositionMs > positionMs;
    }

    AddComment toCommand(UUID projectId, UUID versionId, UUID authorId) {
        return new AddComment(projectId, versionId, authorId, new TimeRange(positionMs, endPositionMs), body);
    }
}
