package dev.higor.trackflow.api.version.domain;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Sequential numbers per project (design D5). The upsert locks the project's counter row until the
 * confirming transaction ends, so simultaneous confirmations get consecutive numbers instead of
 * colliding; {@code unique(project_id, number)} remains as the safety net.
 */
@Repository
public class VersionNumbers {

    private final JdbcClient jdbc;

    public VersionNumbers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Must run inside the confirming transaction; a rollback gives the number back. */
    public int next(UUID projectId) {
        return jdbc.sql("""
                        insert into project_version_counters (project_id, last_number) values (:projectId, 1)
                        on conflict (project_id)
                        do update set last_number = project_version_counters.last_number + 1
                        returning last_number
                        """).param("projectId", projectId).query(Integer.class).single();
    }
}
