package dev.higor.trackflow.api.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ProjectInviteRepository extends ListCrudRepository<ProjectInvite, UUID> {

    Optional<ProjectInvite> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<ProjectInvite> findByTokenHash(String tokenHash);

    /** Not used, not revoked and not expired at {@code now}. */
    @Query("""
            select i from ProjectInvite i
            where i.projectId = :projectId
              and i.acceptedAt is null and i.revokedAt is null and i.expiresAt > :now
            order by i.createdAt desc
            """)
    List<ProjectInvite> findPending(UUID projectId, Instant now);
}
