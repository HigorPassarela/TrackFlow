package dev.higor.trackflow.api.project.application;

import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.domain.ProjectMember;
import dev.higor.trackflow.api.project.domain.ProjectMemberRepository;
import dev.higor.trackflow.api.project.domain.ProjectRepository;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership is checked before anything else, so a non-member gets the same 404 whether the
 * project exists, is archived or never existed.
 */
@Service
class ProjectAccessService implements ProjectAccess {

    private final ProjectMemberRepository members;
    private final ProjectRepository projects;

    ProjectAccessService(ProjectMemberRepository members, ProjectRepository projects) {
        this.members = members;
        this.projects = projects;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectRole requireRole(UUID projectId, UUID userId, ProjectRole minimum) {
        ProjectRole role = members.findMembership(projectId, userId)
                .map(ProjectMember::getRole)
                .orElseThrow(() -> NotFoundException.of("project", projectId));
        if (!role.isAtLeast(minimum)) {
            throw new PermissionDeniedException(
                    "Role %s cannot do what requires %s".formatted(role.name(), minimum.name()));
        }
        return role;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectRole requireRoleOnActiveProject(UUID projectId, UUID userId, ProjectRole minimum) {
        ProjectRole role = requireRole(projectId, userId, minimum);
        projects.findById(projectId)
                .orElseThrow(() -> NotFoundException.of("project", projectId))
                .requireActive();
        return role;
    }
}
