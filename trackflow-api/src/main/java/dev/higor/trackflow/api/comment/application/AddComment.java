package dev.higor.trackflow.api.comment.application;

import dev.higor.trackflow.api.comment.domain.TimeRange;
import java.util.UUID;

/** Command: a member pins a new comment to a version. Built by the controller from the request. */
public record AddComment(UUID projectId, UUID versionId, UUID authorId, TimeRange range, String body) {}
