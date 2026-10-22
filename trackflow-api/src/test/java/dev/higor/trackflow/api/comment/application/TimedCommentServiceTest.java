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
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
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
import org.junit.jupiter.api.DisplayName;
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
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID EDITOR = UUID.fromString("00000000-0000-0000-0000-00000000000e");

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

        CommentView view = service.add(
                new AddComment(PROJECT, VERSION, AUTHOR, TimeRange.point(92_000), "kick embolando com o baixo"));

        assertThat(view.positionMs()).isEqualTo(92_000L);
        assertThat(view.authorName()).isEqualTo("VERXT");
        then(projectActivity).should().record(PROJECT);
        then(events).should().publishEvent(any(CommentAdded.class));
    }

    @Test
    void rejectsCommentOnVersionStillProcessing() {
        VersionSummary processing = new VersionSummary(VERSION, PROJECT, 4, VersionStatus.PROCESSING, null);
        given(versions.findSummary(PROJECT, VERSION)).willReturn(Optional.of(processing));

        assertThatThrownBy(() ->
                        service.add(new AddComment(PROJECT, VERSION, AUTHOR, TimeRange.point(1_000), "cedo demais")))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("version-not-ready"));
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

        then(comments).should().save(argThat(saved -> root.getId().equals(saved.getParentId())));
    }

    @Test
    void listsThreadsWithTheirRepliesAndNames() {
        TimedComment early = persisted(rootCommentAt(30_000));
        TimedComment late = persisted(rootCommentAt(120_000));
        TimedComment replyToLate = persisted(reply(late.getId()));
        givenThreads(false, List.of(early, late), List.of(replyToLate));
        given(users.displayNames(Set.of(AUTHOR))).willReturn(Map.of(AUTHOR, "VERXT"));

        List<CommentView> threads = service.list(PROJECT, VERSION, AUTHOR, false);

        assertThat(threads).extracting(CommentView::positionMs).containsExactly(30_000L, 120_000L);
        assertThat(threads.get(1).replies()).extracting(CommentView::id).containsExactly(replyToLate.getId());
        assertThat(threads.get(0).authorName()).isEqualTo("VERXT");
    }

    @Test
    @DisplayName("Scenario: comment without replies is deleted")
    void deletedCommentWithoutRepliesDisappears() {
        TimedComment deleted = persisted(rootCommentAt(30_000));
        deleted.delete(AUTHOR, ProjectRole.LISTENER, NOW);
        TimedComment kept = persisted(rootCommentAt(60_000));
        givenThreads(false, List.of(deleted, kept), List.of());

        List<CommentView> threads = service.list(PROJECT, VERSION, AUTHOR, false);

        assertThat(threads).extracting(CommentView::id).containsExactly(kept.getId());
    }

    @Test
    @DisplayName("Scenario: comment with replies is deleted")
    void deletedCommentWithRepliesStaysAsRemovedWithoutText() {
        TimedComment root = persisted(rootCommentAt(30_000));
        root.delete(OWNER, ProjectRole.OWNER, NOW);
        givenThreads(false, List.of(root), List.of(persisted(reply(root.getId())), persisted(reply(root.getId()))));

        CommentView thread = service.list(PROJECT, VERSION, AUTHOR, false).getFirst();

        assertThat(thread.removed()).isTrue();
        assertThat(thread.body()).isNull();
        assertThat(thread.replies()).hasSize(2);
    }

    @Test
    void passesTheUnresolvedFilterToTheQuery() {
        givenThreads(true, List.of(), List.of());

        assertThat(service.list(PROJECT, VERSION, AUTHOR, true)).isEmpty();
        then(comments).should().findThreadsOfVersion(PROJECT, VERSION, true);
    }

    @Test
    @DisplayName("Scenario: position beyond the track")
    void rejectsPositionBeyondTheVersionDurationNamingIt() {
        given(versions.findSummary(PROJECT, VERSION)).willReturn(Optional.of(readyVersion(360_000L)));

        assertThatThrownBy(() -> service.add(new AddComment(PROJECT, VERSION, AUTHOR, TimeRange.point(400_000), "oi")))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.code()).isEqualTo("position-beyond-duration");
                    assertThat(error.getMessage()).contains("360000");
                });
        then(comments).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Scenario: author edits")
    void authorEditsOwnCommentAndItShowsAsEdited() {
        TimedComment comment = persisted(rootComment());
        givenComment(comment);
        givenRole(AUTHOR, ProjectRole.LISTENER);
        given(users.displayNames(Set.of(AUTHOR))).willReturn(Map.of(AUTHOR, "VERXT"));

        CommentView view = service.edit(new EditComment(PROJECT, VERSION, comment.getId(), AUTHOR, "kick corrigido"));

        assertThat(view.body()).isEqualTo("kick corrigido");
        assertThat(view.edited()).isTrue();
    }

    @Test
    @DisplayName("Scenario: another member tries to edit")
    void ownerCannotEditSomeoneElsesComment() {
        TimedComment comment = persisted(rootComment());
        givenComment(comment);
        givenRole(OWNER, ProjectRole.OWNER);

        assertThatThrownBy(() -> service.edit(new EditComment(PROJECT, VERSION, comment.getId(), OWNER, "outro texto")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void ownerDeletesSomeoneElsesComment() {
        TimedComment comment = persisted(rootComment());
        givenComment(comment);
        givenRole(OWNER, ProjectRole.OWNER);

        service.delete(PROJECT, VERSION, comment.getId(), OWNER);

        assertThat(comment.isDeleted()).isTrue();
    }

    @Test
    void editorCannotDeleteSomeoneElsesComment() {
        TimedComment comment = persisted(rootComment());
        givenComment(comment);
        givenRole(EDITOR, ProjectRole.EDITOR);

        assertThatThrownBy(() -> service.delete(PROJECT, VERSION, comment.getId(), EDITOR))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(comment.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("Scenario: editor resolves")
    void editorResolvesAListenersCommentAndIsRecorded() {
        TimedComment comment = persisted(rootComment());
        givenComment(comment);
        givenRole(EDITOR, ProjectRole.EDITOR);

        service.resolve(PROJECT, VERSION, comment.getId(), EDITOR);

        assertThat(comment.getResolvedBy()).isEqualTo(EDITOR);
        assertThat(comment.getResolvedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Scenario: listener tries to resolve another person's comment")
    void listenerCannotResolveSomeoneElsesComment() {
        TimedComment comment = persisted(rootComment());
        givenComment(comment);
        givenRole(STRANGER, ProjectRole.LISTENER);

        assertThatThrownBy(() -> service.resolve(PROJECT, VERSION, comment.getId(), STRANGER))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void authorReopensOwnResolvedComment() {
        TimedComment comment = persisted(rootComment());
        comment.resolve(EDITOR, ProjectRole.EDITOR, NOW);
        givenComment(comment);
        givenRole(AUTHOR, ProjectRole.LISTENER);

        service.reopen(PROJECT, VERSION, comment.getId(), AUTHOR);

        assertThat(comment.isResolved()).isFalse();
    }

    @Test
    void listenerCannotReopenSomeoneElsesComment() {
        TimedComment comment = persisted(rootComment());
        comment.resolve(EDITOR, ProjectRole.EDITOR, NOW);
        givenComment(comment);
        givenRole(STRANGER, ProjectRole.LISTENER);

        assertThatThrownBy(() -> service.reopen(PROJECT, VERSION, comment.getId(), STRANGER))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(comment.isResolved()).isTrue();
    }

    private void givenThreads(boolean onlyUnresolved, List<TimedComment> threads, List<TimedComment> replies) {
        given(versions.findSummary(PROJECT, VERSION)).willReturn(Optional.of(readyVersion(360_000L)));
        given(comments.findThreadsOfVersion(PROJECT, VERSION, onlyUnresolved)).willReturn(threads);
        if (!threads.isEmpty()) {
            given(comments.findRepliesOf(
                            PROJECT, threads.stream().map(TimedComment::getId).toList()))
                    .willReturn(replies);
        }
    }

    private void givenComment(TimedComment comment) {
        given(comments.findByIdAndProjectIdAndVersionId(comment.getId(), PROJECT, VERSION))
                .willReturn(Optional.of(comment));
    }

    private void givenRole(UUID user, ProjectRole role) {
        given(projectAccess.requireRoleOnActiveProject(PROJECT, user, ProjectRole.LISTENER))
                .willReturn(role);
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
