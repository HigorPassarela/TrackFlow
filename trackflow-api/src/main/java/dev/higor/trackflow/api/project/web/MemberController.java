package dev.higor.trackflow.api.project.web;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.project.application.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
class MemberController {

    private final MemberService members;

    MemberController(MemberService members) {
        this.members = members;
    }

    @GetMapping
    List<MemberResponse> list(@PathVariable UUID projectId, AuthenticatedUser user) {
        return members.list(projectId, user.id()).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @PatchMapping("/{memberId}")
    MemberResponse changeRole(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @Valid @RequestBody ChangeRoleRequest request,
            AuthenticatedUser user) {
        return MemberResponse.from(members.changeRole(projectId, memberId, request.toRole(), user.id()));
    }

    /** The literal {@code me} wins over {@code {memberId}}, so this route is never shadowed. */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@PathVariable UUID projectId, AuthenticatedUser user) {
        members.leave(projectId, user.id());
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID projectId, @PathVariable UUID memberId, AuthenticatedUser user) {
        members.remove(projectId, memberId, user.id());
    }
}
