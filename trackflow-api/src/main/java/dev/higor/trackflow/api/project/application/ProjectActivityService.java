package dev.higor.trackflow.api.project.application;

import dev.higor.trackflow.api.project.ProjectActivity;
import dev.higor.trackflow.api.project.domain.ProjectRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProjectActivityService implements ProjectActivity {

    private final ProjectRepository projects;
    private final Clock clock;

    ProjectActivityService(ProjectRepository projects, Clock clock) {
        this.projects = projects;
        this.clock = clock;
    }

    /** Joins the caller's transaction, so a rolled-back confirmation does not reorder the list. */
    @Override
    @Transactional
    public void record(UUID projectId) {
        projects.recordActivity(projectId, clock.instant());
    }
}
