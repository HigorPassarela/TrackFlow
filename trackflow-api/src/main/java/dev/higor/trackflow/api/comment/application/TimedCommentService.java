package dev.higor.trackflow.api.comment.application;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import dev.higor.trackflow.api.comment.CommentAdded;
import dev.higor.trackflow.api.comment.domain.TimedComment;
import dev.higor.trackflow.api.comment.domain.TimedCommentRepository;
import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectActivity;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.version.TrackVersions;
import dev.higor.trackflow.api.version.VersionSummary;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of timed comments. Every public method follows the same order: check the caller's
 * role, load what the rule needs, let the entity decide, persist, publish.
 */
@Service
public class TimedCommentService {

    private static final Logger log = LoggerFactory.getLogger(TimedCommentService.class);

    private final TimedCommentRepository comments;
    private final ProjectAccess projectAccess;
    private final ProjectActivity projectActivity;
    private final TrackVersions versions;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TimedCommentService(
            TimedCommentRepository comments,
            ProjectAccess projectAccess,
            ProjectActivity projectActivity,
            TrackVersions versions,
            UserDirectory users,
            ApplicationEventPublisher events,
            Clock clock) {
        this.comments = comments;
        this.projectAccess = projectAccess;
        this.projectActivity = projectActivity;
        this.versions = versions;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CommentView add(AddComment command) {
        projectAccess.requireRoleOnActiveProject(command.projectId(), command.authorId(), ProjectRole.LISTENER);
        VersionSummary version = requireReadyVersion(command.projectId(), command.versionId());
        command.range().requireWithin(Objects.requireNonNull(version.durationMs(), "ready version without duration"));

        TimedComment comment = comments.save(TimedComment.builder()
                .projectId(command.projectId())
                .versionId(command.versionId())
                .authorId(command.authorId())
                .range(command.range())
                .body(command.body())
                .createdAt(clock.instant())
                .build());

        afterCommentStored(comment);
        return CommentView.of(comment, users.displayNames(Set.of(comment.getAuthorId())), List.of());
    }

    @Transactional
    public CommentView reply(ReplyToComment command) {
        projectAccess.requireRoleOnActiveProject(command.projectId(), command.authorId(), ProjectRole.LISTENER);
        TimedComment answered = requireComment(command.projectId(), command.versionId(), command.commentId());

        TimedComment reply = comments.save(TimedComment.builder()
                .projectId(command.projectId())
                .versionId(command.versionId())
                // Replies to replies join the original thread, so threads stay one level deep.
                .parentId(answered.threadId())
                .authorId(command.authorId())
                .body(command.body())
                .createdAt(clock.instant())
                .build());

        afterCommentStored(reply);
        return CommentView.of(reply, users.displayNames(Set.of(reply.getAuthorId())), List.of());
    }

    /** Root comments ordered by position, each with its replies in chronological order. */
    @Transactional(readOnly = true)
    public List<CommentView> list(UUID projectId, UUID versionId, UUID userId, boolean onlyUnresolved) {
        projectAccess.requireRole(projectId, userId, ProjectRole.LISTENER);
        requireVersion(projectId, versionId);

        List<TimedComment> all = comments.findAllOfVersion(projectId, versionId);
        Map<UUID, String> names =
                users.displayNames(all.stream().map(TimedComment::getAuthorId).collect(toSet()));
        Map<UUID, List<TimedComment>> repliesByThread =
                all.stream().filter(TimedComment::isReply).collect(groupingBy(TimedComment::threadId));

        return all.stream()
                .filter(comment -> !comment.isReply())
                .filter(root -> !onlyUnresolved || !root.isResolved())
                .sorted(comparing(TimedComment::getPositionMs))
                .map(root -> CommentView.of(root, names, repliesOf(root, repliesByThread, names)))
                .toList();
    }

    @Transactional
    public void resolve(UUID projectId, UUID versionId, UUID commentId, UUID userId) {
        ProjectRole role = projectAccess.requireRoleOnActiveProject(projectId, userId, ProjectRole.LISTENER);
        TimedComment comment = requireComment(projectId, versionId, commentId);

        comment.resolve(userId, role, clock.instant());
        // No save(): the entity is managed, and the change is flushed when the transaction commits.
        log.info("Comment resolved commentId={} versionId={}", commentId, versionId);
    }

    private void afterCommentStored(TimedComment comment) {
        projectActivity.record(comment.getProjectId());
        events.publishEvent(new CommentAdded(
                comment.getId(),
                comment.getProjectId(),
                comment.getVersionId(),
                comment.getAuthorId(),
                comment.getParentId()));
        log.info(
                "Comment added commentId={} versionId={} reply={}",
                comment.getId(),
                comment.getVersionId(),
                comment.isReply());
    }

    private static List<CommentView> repliesOf(
            TimedComment root, Map<UUID, List<TimedComment>> repliesByThread, Map<UUID, String> names) {
        return repliesByThread.getOrDefault(root.getId(), List.of()).stream()
                .map(reply -> CommentView.of(reply, names, List.of()))
                .toList();
    }

    private TimedComment requireComment(UUID projectId, UUID versionId, UUID commentId) {
        return comments.findByIdAndProjectIdAndVersionId(commentId, projectId, versionId)
                .orElseThrow(() -> NotFoundException.of("comment", commentId));
    }

    private VersionSummary requireVersion(UUID projectId, UUID versionId) {
        return versions.findSummary(projectId, versionId).orElseThrow(() -> NotFoundException.of("version", versionId));
    }

    private VersionSummary requireReadyVersion(UUID projectId, UUID versionId) {
        VersionSummary version = requireVersion(projectId, versionId);
        if (!version.isReady()) {
            throw new BusinessRuleException(
                    "version-not-ready", "Version %d is not ready yet".formatted(version.number()));
        }
        return version;
    }
}
