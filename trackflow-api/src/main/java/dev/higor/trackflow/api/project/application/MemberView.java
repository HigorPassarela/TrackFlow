package dev.higor.trackflow.api.project.application;

import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.domain.ProjectMember;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MemberView(UUID userId, String displayName, ProjectRole role, Instant joinedAt) {

    static MemberView of(ProjectMember member, Map<UUID, String> displayNames) {
        return new MemberView(
                member.userId(),
                displayNames.getOrDefault(member.userId(), ProjectView.UNKNOWN_USER),
                member.getRole(),
                member.getJoinedAt());
    }
}
