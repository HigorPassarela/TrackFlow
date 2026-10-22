package dev.higor.trackflow.api.version.web;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.shared.web.PageResponse;
import dev.higor.trackflow.api.version.application.MediaLinks;
import dev.higor.trackflow.api.version.application.UploadTicket;
import dev.higor.trackflow.api.version.application.VersionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/versions")
class VersionController {

    private final VersionService versions;

    VersionController(VersionService versions) {
        this.versions = versions;
    }

    /** {@code Location} points at the pending version, which becomes readable once confirmed. */
    @PostMapping("/uploads")
    ResponseEntity<UploadResponse> requestUpload(
            @PathVariable UUID projectId, @Valid @RequestBody RequestUploadRequest request, AuthenticatedUser user) {
        UploadTicket ticket = versions.requestUpload(request.toCommand(projectId, user.id()));
        URI location = URI.create("/api/projects/%s/versions/%s"
                .formatted(projectId, ticket.version().id()));
        return ResponseEntity.created(location)
                .body(new UploadResponse(
                        VersionResponse.from(ticket.version()),
                        SignedRequestResponse.from(ticket.source()),
                        SignedRequestResponse.fromNullable(ticket.ableton())));
    }

    @PostMapping("/{versionId}/confirm")
    VersionResponse confirm(@PathVariable UUID projectId, @PathVariable UUID versionId, AuthenticatedUser user) {
        return VersionResponse.from(versions.confirm(projectId, versionId, user.id()));
    }

    @GetMapping
    PageResponse<VersionResponse> history(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size,
            AuthenticatedUser user) {
        return versions.history(projectId, user.id(), page, size).map(VersionResponse::from);
    }

    @GetMapping("/{versionId}")
    VersionResponse get(@PathVariable UUID projectId, @PathVariable UUID versionId, AuthenticatedUser user) {
        return VersionResponse.from(versions.get(projectId, versionId, user.id()));
    }

    @PatchMapping("/{versionId}")
    VersionResponse update(
            @PathVariable UUID projectId,
            @PathVariable UUID versionId,
            @Valid @RequestBody UpdateVersionRequest request,
            AuthenticatedUser user) {
        return VersionResponse.from(versions.edit(request.toCommand(projectId, versionId, user.id())));
    }

    @GetMapping("/{versionId}/playback")
    PlaybackResponse playback(@PathVariable UUID projectId, @PathVariable UUID versionId, AuthenticatedUser user) {
        MediaLinks links = versions.playback(projectId, versionId, user.id());
        return new PlaybackResponse(
                links.primary().url(),
                links.secondary() != null ? links.secondary().url() : null,
                links.primary().expiresAt());
    }

    @GetMapping("/{versionId}/download")
    DownloadResponse download(@PathVariable UUID projectId, @PathVariable UUID versionId, AuthenticatedUser user) {
        MediaLinks links = versions.download(projectId, versionId, user.id());
        return new DownloadResponse(
                links.primary().url(),
                links.secondary() != null ? links.secondary().url() : null,
                links.primary().expiresAt());
    }

    /** 202: the version goes back to "uploaded" now; processing happens in the background. */
    @PostMapping("/{versionId}/reprocess")
    @ResponseStatus(HttpStatus.ACCEPTED)
    VersionResponse reprocess(@PathVariable UUID projectId, @PathVariable UUID versionId, AuthenticatedUser user) {
        return VersionResponse.from(versions.reprocess(projectId, versionId, user.id()));
    }

    record UploadResponse(
            VersionResponse version,
            SignedRequestResponse upload,
            @Nullable SignedRequestResponse abletonUpload) {}

    record PlaybackResponse(URI previewUrl, @Nullable URI peaksUrl, Instant expiresAt) {}

    record DownloadResponse(URI originalUrl, @Nullable URI abletonProjectUrl, Instant expiresAt) {}
}
