package dev.higor.trackflow.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class FlywayMigrationIT {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Test
    void appliesEveryMigrationOnStartup() {
        var migrations = flyway.info().all();

        assertThat(migrations)
                .isNotEmpty()
                .allSatisfy(migration -> assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS));
    }

    @Test
    void createsTheEventPublicationRegistry() {
        var rows = jdbc.sql("select count(*) from event_publication")
                .query(Long.class)
                .single();

        assertThat(rows).isZero();
    }
}
