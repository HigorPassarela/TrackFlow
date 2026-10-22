package dev.higor.trackflow.api.comment.web;

import dev.higor.trackflow.api.comment.application.TimedCommentService;
import dev.higor.trackflow.api.identity.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Package-private: nothing outside this package needs the controller. Each method converts the
 * request, calls one service method and converts the result. No rules here.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/versions/{versionId}/comments")
class TimedCommentController {

    private final TimedCommentService service;

    TimedCommentController(TimedCommentService service) {
        this.service = service;
    }

    @GetMapping
    List<CommentResponse> list(
            @PathVariable UUID projectId,
            @PathVariable UUID versionId,
            @RequestParam(defaultValue = "false") boolean onlyUnresolved,
            AuthenticatedUser user) {
        return service.list(projectId, versionId, user.id(), onlyUnresolved).stream()
                .map(CommentResponse::from)
                .toList();
    }

    /** No {@code Location} header: comments have no individual GET, they are read per version. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CommentResponse create(
            @PathVariable UUID projectId,
            @PathVariable UUID versionId,
            @Valid @RequestBody CreateCommentRequest request,
            AuthenticatedUser user) {
        return CommentResponse.from(service.add(request.toCommand(projectId, versionId, user.id())));
    }

    @PostMapping("/{commentId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    CommentResponse reply(
            @PathVariable UUID projectId,
            @PathVariable UUID versionId,
            @PathVariable UUID commentId,
            @Valid @RequestBody ReplyRequest request,
            AuthenticatedUser user) {
        return CommentResponse.from(service.reply(request.toCommand(projectId, versionId, commentId, user.id())));
    }

    @PatchMapping("/{commentId}")
    CommentResponse edit(
            @PathVariable UUID projectId,
            @PathVariable UUID versionId,
            @PathVariable UUID commentId,
            @Valid @RequestBody EditCommentRequest request,
            AuthenticatedUser user) {
        return CommentResponse.from(service.edit(request.toCommand(projectId, versionId, commentId, user.id())));
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID versionId,
            @PathVariable UUID commentId,
            AuthenticatedUser user) {
        service.delete(projectId, versionId, commentId, user.id());
    }

    @PostMapping("/{commentId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resolve(
            @PathVariable UUID projectId,
            @PathVariable UUID versionId,
            @PathVariable UUID commentId,
            AuthenticatedUser user) {
        service.resolve(projectId, versionId, commentId, user.id());
    }

    @PostMapping("/{commentId}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reopen(
            @PathVariable UUID projectId,
            @PathVariable UUID versionId,
            @PathVariable UUID commentId,
            AuthenticatedUser user) {
        service.reopen(projectId, versionId, commentId, user.id());
    }
}
