package dev.higor.trackflow.api.comment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import dev.higor.trackflow.api.comment.CommentAdded;
import dev.higor.trackflow.api.comment.domain.TimeRange;
import dev.higor.trackflow.api.comment.domain.TimedComment;
import dev.higor.trackflow.api.comment.domain.TimedCommentRepository;
import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectActivity;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.version.TrackVersions;
import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.VersionSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Other modules' public APIs are mocked: this test is about the comment rules, not about how
 * permissions or versions are implemented.
 */
@ExtendWith(MockitoExtension.class)
class TimedCommentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T22:00:00Z");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000f");

    @Mock
    private TimedCommentRepository comments;

    @Mock
    private ProjectAccess projectAccess;

    @Mock
    private ProjectActivity projectActivity;

    @Mock
    private TrackVersions versions;

    @Mock
    private UserDirectory users;

    @Mock
    private ApplicationEventPublisher events;

    private TimedCommentService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new TimedCommentService(comments, projectAccess, projectActivity, versions, users, events, clock);
    }

    @Test
    void addsCommentRecordsActivityAndPublishesEvent() {
        given(versions.findSummary(PROJECT, VERSION)).willReturn(Optional.of(readyVersion(360_000L)));
        given(comments.save(any(TimedComment.class))).willAnswer(invocation -> persisted(invocation.getArgument(0)));
        given(users.displayNames(Set.of(AUTHOR))).willReturn(Map.of(AUTHOR, "VERXT"));

        CommentView view = service.add(new AddComment(
                PROJECT, VERSION, AUTHOR, TimeRange.point(92_000), "kick embolando com o baixo"));

        assertThat(view.positionMs()).isEqualTo(92_000L);
        assertThat(view.authorName()).isEqualTo("VERXT");
        then(projectActivity).should().record(PROJECT);
        then(events).should().publishEvent(any(CommentAdded.class));
    }

    @Test
    void rejectsCommentOnVersionStillProcessing() {
        VersionSummary processing = new VersionSummary(VERSION, PROJECT, 4, VersionStatus.PROCESSING, null);
        given(versions.findSummary(PROJECT, VERSION)).willReturn(Optional.of(processing));

        assertThatThrownBy(() -> service.add(new AddComment(PROJECT, VERSION, AUTHOR, TimeRange.point(1_000), "cedo demais")))
                .isInstanceOfSatisfying(BusinessRuleException.class, error ->
                        assertThat(error.code()).isEqualTo("version-not-ready"));
        then(comments).shouldHaveNoInteractions();
    }

    @Test
    void nonMemberGetsNotFoundAndNothingIsStored() {
        given(projectAccess.requireRoleOnActiveProject(PROJECT, STRANGER, ProjectRole.LISTENER))
                .willThrow(NotFoundException.of("project", PROJECT));

        assertThatThrownBy(() -> service.add(new AddComment(PROJECT, VERSION, STRANGER, TimeRange.point(1_000), "oi")))
                .isInstanceOf(NotFoundException.class);
        then(comments).shouldHaveNoInteractions();
        then(events).shouldHaveNoInteractions();
    }

    @Test
    void replyToAReplyJoinsTheOriginalThread() {
        TimedComment root = persisted(rootComment());
        TimedComment firstReply = persisted(reply(root.getId()));
        given(comments.findByIdAndProjectIdAndVersionId(firstReply.getId(), PROJECT, VERSION))
                .willReturn(Optional.of(firstReply));
        given(comments.save(any(TimedComment.class))).willAnswer(invocation -> persisted(invocation.getArgument(0)));
        given(users.displayNames(Set.of(AUTHOR))).willReturn(Map.of(AUTHOR, "VERXT"));

        service.reply(new ReplyToComment(PROJECT, VERSION, firstReply.getId(), AUTHOR, "corrigi na v5"));

        then(comments).should().save(argThat(saved ->
                root.getId().equals(saved.getParentId())));
    }

    @Test
    void listsRootsByPositionWithRepliesInOrder() {
        TimedComment late = persisted(rootCommentAt(120_000));
        TimedComment early = persisted(rootCommentAt(30_000));
        TimedComment replyToLate = persisted(reply(late.getId()));
        given(versions.findSummary(PROJECT, VERSION)).willReturn(Optional.of(readyVersion(360_000L)));
        given(comments.findAllOfVersion(PROJECT, VERSION)).willReturn(List.of(late, early, replyToLate));
        given(users.displayNames(Set.of(AUTHOR))).willReturn(Map.of(AUTHOR, "VERXT"));

        List<CommentView> threads = service.list(PROJECT, VERSION, AUTHOR, false);

        assertThat(threads).extracting(CommentView::positionMs).containsExactly(30_000L, 120_000L);
        assertThat(threads.get(1).replies()).extracting(CommentView::id).containsExactly(replyToLate.getId());
    }

    private static VersionSummary readyVersion(long durationMs) {
        return new VersionSummary(VERSION, PROJECT, 4, VersionStatus.READY, durationMs);
    }

    private static TimedComment rootComment() {
        return rootCommentAt(92_000);
    }

    private static TimedComment rootCommentAt(long positionMs) {
        return TimedComment.builder()
                .projectId(PROJECT)
                .versionId(VERSION)
                .authorId(AUTHOR)
                .range(TimeRange.point(positionMs))
                .body("comentário em " + positionMs)
                .createdAt(NOW)
                .build();
    }

    private static TimedComment reply(UUID rootId) {
        return TimedComment.builder()
                .projectId(PROJECT)
                .versionId(VERSION)
                .parentId(rootId)
                .authorId(AUTHOR)
                .body("resposta")
                .createdAt(NOW)
                .build();
    }

    /** Simulates what Hibernate does on persist: the id only exists after saving. */
    private static TimedComment persisted(TimedComment comment) {
        ReflectionTestUtils.setField(comment, "id", UUID.randomUUID());
        return comment;
    }
}
