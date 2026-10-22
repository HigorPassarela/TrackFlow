package dev.higor.trackflow.api.comment.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface TimedCommentRepository extends ListCrudRepository<TimedComment, UUID> {

    /**
     * Always scoped by project and version: an id that exists in another project must behave as
     * not found, otherwise ids could be guessed across projects.
     */
    Optional<TimedComment> findByIdAndProjectIdAndVersionId(UUID id, UUID projectId, UUID versionId);

    /**
     * Roots and replies of a version in creation order. Threads are assembled in memory: a version
     * has at most a few hundred comments, and one query beats one per thread.
     */
    @Query("""
            select c from TimedComment c
            where c.projectId = :projectId and c.versionId = :versionId
            order by c.createdAt
            """)
    List<TimedComment> findAllOfVersion(UUID projectId, UUID versionId);
}
