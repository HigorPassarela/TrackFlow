package dev.higor.trackflow.api.comment.application;

import java.util.UUID;

/**
 * Command: a member answers a comment.
 *
 * @param commentId the comment being answered; may itself be a reply
 */
public record ReplyToComment(UUID projectId, UUID versionId, UUID commentId, UUID authorId, String body) {}
