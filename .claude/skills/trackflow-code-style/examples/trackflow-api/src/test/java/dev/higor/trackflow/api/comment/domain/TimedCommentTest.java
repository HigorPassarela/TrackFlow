package dev.higor.trackflow.api.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimedCommentTest {

    private static final Instant NOW = Instant.parse("2026-10-21T22:00:00Z");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER_MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void rejectsReplyWithItsOwnPosition() {
        assertThatIllegalArgumentException().isThrownBy(() -> TimedComment.builder()
                .projectId(PROJECT)
                .versionId(VERSION)
                .parentId(UUID.randomUUID())
                .authorId(AUTHOR)
                .range(TimeRange.point(1_000))
                .body("resposta")
                .createdAt(NOW)
                .build());
    }

    @Test
    void authorCanResolveOwnCommentEvenAsListener() {
        TimedComment comment = rootCommentBy(AUTHOR);

        comment.resolve(AUTHOR, ProjectRole.LISTENER, NOW);

        assertThat(comment.isResolved()).isTrue();
        assertThat(comment.getResolvedBy()).isEqualTo(AUTHOR);
    }

    @Test
    void listenerCannotResolveSomeoneElsesComment() {
        TimedComment comment = rootCommentBy(AUTHOR);

        assertThatThrownBy(() -> comment.resolve(OTHER_MEMBER, ProjectRole.LISTENER, NOW))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(comment.isResolved()).isFalse();
    }

    @Test
    void resolvingTwiceKeepsWhoResolvedFirst() {
        TimedComment comment = rootCommentBy(AUTHOR);

        comment.resolve(AUTHOR, ProjectRole.LISTENER, NOW);
        comment.resolve(OTHER_MEMBER, ProjectRole.EDITOR, NOW.plusSeconds(60));

        assertThat(comment.getResolvedBy()).isEqualTo(AUTHOR);
        assertThat(comment.getResolvedAt()).isEqualTo(NOW);
    }

    @Test
    void stripsBodyWhitespace() {
        TimedComment comment = TimedComment.builder()
                .projectId(PROJECT)
                .versionId(VERSION)
                .authorId(AUTHOR)
                .range(TimeRange.point(92_000))
                .body("  kick embolando com o baixo \n")
                .createdAt(NOW)
                .build();

        assertThat(comment.getBody()).isEqualTo("kick embolando com o baixo");
    }

    private static TimedComment rootCommentBy(UUID author) {
        return TimedComment.builder()
                .projectId(PROJECT)
                .versionId(VERSION)
                .authorId(author)
                .range(TimeRange.point(92_000))
                .body("kick embolando com o baixo")
                .createdAt(NOW)
                .build();
    }
}
