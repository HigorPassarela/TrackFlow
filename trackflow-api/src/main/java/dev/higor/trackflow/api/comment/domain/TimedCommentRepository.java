package dev.higor.trackflow.api.comment.domain;

import java.util.Collection;
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
     * Root comments of a version in track order; equal positions keep the order they were written
     * in. With {@code onlyUnresolved}, resolved threads are left out entirely.
     */
    @Query("""
            select c from TimedComment c
            where c.projectId = :projectId and c.versionId = :versionId and c.parentId is null
              and (:onlyUnresolved = false or c.resolvedAt is null)
            order by c.positionMs, c.createdAt, c.id
            """)
    List<TimedComment> findThreadsOfVersion(UUID projectId, UUID versionId, boolean onlyUnresolved);

    /** Replies of the given threads in chronological order, fetched in one query for all threads. */
    @Query("""
            select c from TimedComment c
            where c.projectId = :projectId and c.parentId in :threadIds
            order by c.createdAt, c.id
            """)
    List<TimedComment> findRepliesOf(UUID projectId, Collection<UUID> threadIds);
}
