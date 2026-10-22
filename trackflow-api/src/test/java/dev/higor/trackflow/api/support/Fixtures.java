package dev.higor.trackflow.api.support;

import dev.higor.trackflow.api.identity.domain.User;
import dev.higor.trackflow.api.identity.domain.UserFixtures;
import dev.higor.trackflow.api.identity.domain.UserRepository;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.application.CreateProject;
import dev.higor.trackflow.api.project.application.ProjectService;
import dev.higor.trackflow.api.project.domain.ProjectFixtures;
import dev.higor.trackflow.api.project.domain.ProjectMemberRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds real rows through the application's own services for integration tests, so foreign keys
 * and the owner membership are always consistent.
 */
@Component
public class Fixtures {

    private final UserRepository users;
    private final ProjectService projects;
    private final ProjectMemberRepository members;

    public Fixtures(UserRepository users, ProjectService projects, ProjectMemberRepository members) {
        this.users = users;
        this.projects = projects;
        this.members = members;
    }

    public User user(String subject) {
        return users.save(UserFixtures.githubUser(subject + "-" + UUID.randomUUID()));
    }

    public UUID projectOwnedBy(User owner) {
        return projects.create(new CreateProject(owner.getId(), "Ibiza Vibes", null, "tech house"))
                .id();
    }

    public void join(UUID projectId, User user, ProjectRole role) {
        members.save(ProjectFixtures.member(projectId, user.getId(), role));
    }
}
