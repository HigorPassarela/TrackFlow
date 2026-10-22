package dev.higor.trackflow.api.project.web;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.project.application.ProjectService;
import dev.higor.trackflow.api.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
class ProjectController {

    private final ProjectService projects;

    ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    PageResponse<ProjectSummaryResponse> listMine(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size,
            AuthenticatedUser user) {
        return projects.listMine(user.id(), includeArchived, page, size).map(ProjectSummaryResponse::from);
    }

    /** {@code Location} points at the project, which has its own {@code GET}. */
    @PostMapping
    ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request, AuthenticatedUser user) {
        ProjectResponse created = ProjectResponse.from(projects.create(request.toCommand(user.id())));
        return ResponseEntity.created(URI.create("/api/projects/" + created.id()))
                .body(created);
    }

    @GetMapping("/{projectId}")
    ProjectResponse get(@PathVariable UUID projectId, AuthenticatedUser user) {
        return ProjectResponse.from(projects.get(projectId, user.id()));
    }

    @PatchMapping("/{projectId}")
    ProjectResponse update(
            @PathVariable UUID projectId, @Valid @RequestBody UpdateProjectRequest request, AuthenticatedUser user) {
        return ProjectResponse.from(projects.edit(request.toCommand(projectId, user.id())));
    }

    @PostMapping("/{projectId}/archive")
    ProjectResponse archive(@PathVariable UUID projectId, AuthenticatedUser user) {
        return ProjectResponse.from(projects.archive(projectId, user.id()));
    }

    @PostMapping("/{projectId}/unarchive")
    ProjectResponse unarchive(@PathVariable UUID projectId, AuthenticatedUser user) {
        return ProjectResponse.from(projects.unarchive(projectId, user.id()));
    }
}
