package dev.higor.trackflow.api.version.web;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.version.application.CreatedShareLink;
import dev.higor.trackflow.api.version.application.ListenView;
import dev.higor.trackflow.api.version.application.ShareLinkService;
import dev.higor.trackflow.api.version.domain.ShareLink;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ShareLinkController {

    private final ShareLinkService links;

    ShareLinkController(ShareLinkService links) {
        this.links = links;
    }

    /** No {@code Location}: links have no {@code GET} for members; the token is shown only here. */
    @PostMapping("/api/projects/{projectId}/versions/{versionId}/share-links")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedShareLinkResponse create(
            @PathVariable UUID projectId,
            @PathVariable UUID versionId,
            @Valid @RequestBody(required = false) @Nullable CreateShareLinkRequest request,
            AuthenticatedUser user) {
        Integer days = request == null ? null : request.validDays();
        CreatedShareLink created = links.create(projectId, versionId, user.id(), days);
        return new CreatedShareLinkResponse(
                created.id(),
                created.versionId(),
                created.expiresAt(),
                created.token(),
                "/api/listen/" + created.token());
    }

    @DeleteMapping("/api/projects/{projectId}/share-links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID projectId, @PathVariable UUID linkId, AuthenticatedUser user) {
        links.revoke(projectId, linkId, user.id());
    }

    /** Public: the security rules let {@code /api/listen/**} through without a session. */
    @GetMapping("/api/listen/{token}")
    ListenResponse listen(@PathVariable String token) {
        ListenView view = links.listen(token);
        return new ListenResponse(
                view.projectTitle(),
                view.versionNumber(),
                view.authorName(),
                view.durationMs(),
                view.preview().url(),
                view.peaks().url(),
                view.preview().expiresAt());
    }

    record CreateShareLinkRequest(
            @Nullable @Min(ShareLink.MIN_DAYS) @Max(ShareLink.MAX_DAYS)
            Integer validDays) {}

    record CreatedShareLinkResponse(UUID id, UUID versionId, Instant expiresAt, String token, String listenPath) {}

    record ListenResponse(
            String projectTitle,
            @Nullable Integer versionNumber,
            String authorName,
            @Nullable Long durationMs,
            URI previewUrl,
            URI peaksUrl,
            Instant urlsExpireAt) {}
}
