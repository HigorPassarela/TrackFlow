package dev.higor.trackflow.api.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.higor.trackflow.api.support.PostgresTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/**
 * Runs against PostgreSQL with the real Flyway migrations, so the check constraints and column
 * types are exercised too. An in-memory database would hide exactly the bugs this test exists for.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
@Sql("/fixtures/project-with-ready-version.sql")
class TimedCommentRepositoryIT {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Instant NOW = Instant.parse("2026-10-21T22:00:00Z");

    @Autowired
    private TimedCommentRepository comments;

    @Test
    void findsCommentOnlyThroughItsOwnProject() {
        TimedComment saved = comments.save(rootCommentAt(92_000, NOW));

        assertThat(comments.findByIdAndProjectIdAndVersionId(saved.getId(), PROJECT, VERSION))
                .isPresent();
        assertThat(comments.findByIdAndProjectIdAndVersionId(saved.getId(), OTHER_PROJECT, VERSION))
                .isEmpty();
    }

    @Test
    void listsCommentsOfAVersionInCreationOrder() {
        TimedComment first = comments.save(rootCommentAt(120_000, NOW));
        TimedComment second = comments.save(rootCommentAt(30_000, NOW.plusSeconds(5)));

        List<TimedComment> all = comments.findAllOfVersion(PROJECT, VERSION);

        assertThat(all).extracting(TimedComment::getId).containsExactly(first.getId(), second.getId());
    }

    private static TimedComment rootCommentAt(long positionMs, Instant createdAt) {
        return TimedComment.builder()
                .projectId(PROJECT)
                .versionId(VERSION)
                .authorId(AUTHOR)
                .range(TimeRange.point(positionMs))
                .body("comentário em " + positionMs)
                .createdAt(createdAt)
                .build();
    }
}
