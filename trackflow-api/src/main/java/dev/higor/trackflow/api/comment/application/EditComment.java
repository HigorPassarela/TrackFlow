package dev.higor.trackflow.api.comment.application;

import java.util.UUID;

/** Command: the author rewrites the text of a comment or reply. */
public record EditComment(UUID projectId, UUID versionId, UUID commentId, UUID userId, String body) {}
