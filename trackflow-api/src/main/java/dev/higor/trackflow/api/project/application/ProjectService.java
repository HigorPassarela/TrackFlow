package dev.higor.trackflow.api.project.application;

import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.domain.Project;
import dev.higor.trackflow.api.project.domain.ProjectListItem;
import dev.higor.trackflow.api.project.domain.ProjectMember;
import dev.higor.trackflow.api.project.domain.ProjectMemberRepository;
import dev.higor.trackflow.api.project.domain.ProjectRepository;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.web.PageResponse;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of a project itself. Members and invites live in their own services. */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectAccess access;
    private final UserDirectory users;
    private final Clock clock;

    public ProjectService(
            ProjectRepository projects,
            ProjectMemberRepository members,
            ProjectAccess access,
            UserDirectory users,
            Clock clock) {
        this.projects = projects;
        this.members = members;
        this.access = access;
        this.users = users;
        this.clock = clock;
    }

    /** The project and its owner membership are stored together: a project never exists without owner. */
    @Transactional
    public ProjectView create(CreateProject command) {
        var now = clock.instant();
        Project project = projects.save(Project.builder()
                .title(command.title())
                .description(command.description())
                .genre(command.genre())
                .ownerId(command.ownerId())
                .createdAt(now)
                .build());
        members.save(ProjectMember.builder()
                .projectId(project.getId())
                .userId(command.ownerId())
                .role(ProjectRole.OWNER)
                .joinedAt(now)
                .build());
        log.info("Project created projectId={} ownerId={}", project.getId(), command.ownerId());
        return view(project, ProjectRole.OWNER);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectListItem> listMine(UUID userId, boolean includeArchived, int page, int size) {
        var pageable = PageRequest.of(page, size);
        return PageResponse.of(projects.findMine(userId, includeArchived, pageable), item -> item);
    }

    @Transactional(readOnly = true)
    public ProjectView get(UUID projectId, UUID userId) {
        ProjectRole role = access.requireRole(projectId, userId, ProjectRole.LISTENER);
        return view(require(projectId), role);
    }

    @Transactional
    public ProjectView edit(EditProject command) {
        ProjectRole role = access.requireRoleOnActiveProject(command.projectId(), command.userId(), ProjectRole.EDITOR);
        Project project = require(command.projectId());
        project.edit(
                Objects.requireNonNullElse(command.title(), project.getTitle()),
                command.description() != null ? command.description() : project.getDescription(),
                command.genre() != null ? command.genre() : project.getGenre(),
                clock.instant());
        log.info("Project edited projectId={} userId={}", command.projectId(), command.userId());
        return view(project, role);
    }

    @Transactional
    public ProjectView archive(UUID projectId, UUID userId) {
        ProjectRole role = access.requireRole(projectId, userId, ProjectRole.OWNER);
        Project project = require(projectId);
        project.archive(clock.instant());
        log.info("Project archived projectId={}", projectId);
        return view(project, role);
    }

    @Transactional
    public ProjectView unarchive(UUID projectId, UUID userId) {
        ProjectRole role = access.requireRole(projectId, userId, ProjectRole.OWNER);
        Project project = require(projectId);
        project.unarchive(clock.instant());
        log.info("Project unarchived projectId={}", projectId);
        return view(project, role);
    }

    private Project require(UUID projectId) {
        return projects.findById(projectId).orElseThrow(() -> NotFoundException.of("project", projectId));
    }

    private ProjectView view(Project project, ProjectRole role) {
        String ownerName = users.displayNames(Set.of(project.getOwnerId()))
                .getOrDefault(project.getOwnerId(), ProjectView.UNKNOWN_USER);
        return ProjectView.of(project, ownerName, role);
    }
}
