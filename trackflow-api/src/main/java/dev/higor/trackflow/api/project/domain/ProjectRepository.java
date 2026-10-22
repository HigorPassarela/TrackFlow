package dev.higor.trackflow.api.project.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ProjectRepository extends ListCrudRepository<Project, UUID> {

    /**
     * Projects the user belongs to, most recently active first. The id breaks ties so pages never
     * overlap when two projects share the same activity time.
     */
    @Query(value = """
                    select new dev.higor.trackflow.api.project.domain.ProjectListItem(
                        p.id, p.title, p.genre, m.role, p.archivedAt, p.lastActivityAt)
                    from Project p join ProjectMember m on m.id.projectId = p.id
                    where m.id.userId = :userId and (:includeArchived = true or p.archivedAt is null)
                    order by p.lastActivityAt desc, p.id desc
                    """, countQuery = """
                    select count(p) from Project p join ProjectMember m on m.id.projectId = p.id
                    where m.id.userId = :userId and (:includeArchived = true or p.archivedAt is null)
                    """)
    Page<ProjectListItem> findMine(UUID userId, boolean includeArchived, Pageable pageable);

    /**
     * Atomic bump without loading the entity, so concurrent activity from versions and comments
     * never fails on the optimistic lock of an unrelated edit.
     */
    @Modifying
    @Query("update Project p set p.lastActivityAt = :at where p.id = :projectId and p.lastActivityAt < :at")
    int recordActivity(UUID projectId, Instant at);
}
