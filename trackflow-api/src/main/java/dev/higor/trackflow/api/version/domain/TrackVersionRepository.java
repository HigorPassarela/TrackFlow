package dev.higor.trackflow.api.version.domain;

import dev.higor.trackflow.api.version.VersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface TrackVersionRepository extends ListCrudRepository<TrackVersion, UUID> {

    /** Includes pending uploads; only the version module itself may see those. */
    Optional<TrackVersion> findByIdAndProjectId(UUID id, UUID projectId);

    /** History: confirmed versions only, newest first. */
    @Query(value = """
                    select v from TrackVersion v
                    where v.projectId = :projectId and v.status <> dev.higor.trackflow.api.version.VersionStatus.PENDING_UPLOAD
                    order by v.number desc
                    """, countQuery = """
                    select count(v) from TrackVersion v
                    where v.projectId = :projectId and v.status <> dev.higor.trackflow.api.version.VersionStatus.PENDING_UPLOAD
                    """)
    Page<TrackVersion> findHistory(UUID projectId, Pageable pageable);

    List<TrackVersion> findByStatusAndCreatedAtBefore(VersionStatus status, Instant createdBefore);

    /**
     * Deletes the version only if it is still pending, so a confirmation that wins the race is never
     * discarded. Returns how many rows went away (0 or 1).
     */
    @Modifying
    @Query("""
            delete from TrackVersion v
            where v.id = :id and v.status = dev.higor.trackflow.api.version.VersionStatus.PENDING_UPLOAD
            """)
    int deleteIfStillPending(UUID id);
}
