package dev.higor.trackflow.api.project.web;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.project.application.InviteService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class InviteController {

    private final InviteService invites;

    InviteController(InviteService invites) {
        this.invites = invites;
    }

    @GetMapping("/api/projects/{projectId}/invites")
    List<InviteResponse> listPending(@PathVariable UUID projectId, AuthenticatedUser user) {
        return invites.listPending(projectId, user.id()).stream()
                .map(InviteResponse::from)
                .toList();
    }

    /** No {@code Location}: invites have no individual {@code GET}. */
    @PostMapping("/api/projects/{projectId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedInviteResponse create(
            @PathVariable UUID projectId, @Valid @RequestBody CreateInviteRequest request, AuthenticatedUser user) {
        return CreatedInviteResponse.from(invites.create(projectId, request.toRole(), user.id()));
    }

    @DeleteMapping("/api/projects/{projectId}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID projectId, @PathVariable UUID inviteId, AuthenticatedUser user) {
        invites.revoke(projectId, inviteId, user.id());
    }

    @PostMapping("/api/invites/{token}/accept")
    AcceptInviteResponse accept(@PathVariable String token, AuthenticatedUser user) {
        var accepted = invites.accept(token, user.id());
        return new AcceptInviteResponse(
                accepted.projectId(), accepted.role().name().toLowerCase(Locale.ROOT));
    }

    record AcceptInviteResponse(UUID projectId, String role) {}
}
