package dev.higor.trackflow.api.comment;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published after a comment or a reply is stored. Carries ids only: listeners that need more load
 * it through the public API instead of trusting a snapshot in the event.
 *
 * @param parentId the thread's root comment when this is a reply
 */
public record CommentAdded(
        UUID commentId,
        UUID projectId,
        UUID versionId,
        UUID authorId,
        @Nullable UUID parentId) {}
