package dev.higor.trackflow.api.project.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

public interface ProjectMemberRepository extends ListCrudRepository<ProjectMember, ProjectMemberId> {

    default Optional<ProjectMember> findMembership(UUID projectId, UUID userId) {
        return findById(new ProjectMemberId(projectId, userId));
    }

    List<ProjectMember> findAllByIdProjectId(UUID projectId);
}
