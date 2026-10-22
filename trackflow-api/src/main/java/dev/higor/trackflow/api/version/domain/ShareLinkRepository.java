package dev.higor.trackflow.api.version.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

public interface ShareLinkRepository extends ListCrudRepository<ShareLink, UUID> {

    Optional<ShareLink> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<ShareLink> findByTokenHash(String tokenHash);
}
