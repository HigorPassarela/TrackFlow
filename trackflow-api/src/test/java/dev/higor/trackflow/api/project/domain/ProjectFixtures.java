package dev.higor.trackflow.api.project.domain;

import dev.higor.trackflow.api.project.ProjectRole;
import java.time.Instant;
import java.util.UUID;

public final class ProjectFixtures {

    public static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    public static final Instant CREATED_AT = Instant.parse("2026-10-21T22:00:00Z");

    private ProjectFixtures() {}

    public static Project activeProject() {
        return Project.builder()
                .title("Ibiza Vibes")
                .genre("tech house")
                .ownerId(OWNER)
                .createdAt(CREATED_AT)
                .build();
    }

    public static Project archivedProject() {
        Project project = activeProject();
        project.archive(CREATED_AT.plusSeconds(60));
        return project;
    }

    public static ProjectMember member(UUID projectId, UUID userId, ProjectRole role) {
        return ProjectMember.builder()
                .projectId(projectId)
                .userId(userId)
                .role(role)
                .joinedAt(CREATED_AT)
                .build();
    }
}
