package dev.higor.trackflow.api.project;

import java.util.UUID;

/**
 * Public API of the project module for permission checks. Every operation on a project resource
 * calls one of these methods before reading anything.
 */
public interface ProjectAccess {

    /**
     * Returns the caller's role in the project.
     *
     * @throws dev.higor.trackflow.api.shared.error.NotFoundException when the project does not
     *     exist or the user is not a member (same answer on purpose)
     * @throws dev.higor.trackflow.api.shared.error.PermissionDeniedException when the role is below
     *     {@code minimum}
     */
    ProjectRole requireRole(UUID projectId, UUID userId, ProjectRole minimum);

    /**
     * Same as {@link #requireRole} and also rejects archived projects with a {@code
     * project-archived} business rule error. Use it for every write.
     */
    ProjectRole requireRoleOnActiveProject(UUID projectId, UUID userId, ProjectRole minimum);
}
