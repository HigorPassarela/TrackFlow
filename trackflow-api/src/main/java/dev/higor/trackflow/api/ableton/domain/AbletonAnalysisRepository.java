package dev.higor.trackflow.api.ableton.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

public interface AbletonAnalysisRepository extends ListCrudRepository<AbletonAnalysis, UUID> {

    Optional<AbletonAnalysis> findByVersionIdAndProjectId(UUID versionId, UUID projectId);
}
