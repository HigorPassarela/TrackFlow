package dev.higor.trackflow.api.project.application;

import dev.higor.trackflow.api.project.ProjectDirectory;
import dev.higor.trackflow.api.project.domain.Project;
import dev.higor.trackflow.api.project.domain.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProjectDirectoryService implements ProjectDirectory {

    private final ProjectRepository projects;

    ProjectDirectoryService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findTitle(UUID projectId) {
        return projects.findById(projectId).map(Project::getTitle);
    }
}
