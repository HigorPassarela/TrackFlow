package dev.higor.trackflow.api.version.application;

import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectDirectory;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.security.TokenGenerator;
import dev.higor.trackflow.api.storage.ObjectStorage;
import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.domain.ShareLink;
import dev.higor.trackflow.api.version.domain.ShareLinkRepository;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Time-limited public links to listen to one ready version. */
@Service
public class ShareLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShareLinkService.class);

    private final ShareLinkRepository links;
    private final TrackVersionRepository versions;
    private final ObjectStorage storage;
    private final ProjectAccess access;
    private final ProjectDirectory projects;
    private final UserDirectory users;
    private final TokenGenerator tokens;
    private final VersionProperties properties;
    private final Clock clock;

    public ShareLinkService(
            ShareLinkRepository links,
            TrackVersionRepository versions,
            ObjectStorage storage,
            ProjectAccess access,
            ProjectDirectory projects,
            UserDirectory users,
            TokenGenerator tokens,
            VersionProperties properties,
            Clock clock) {
        this.links = links;
        this.versions = versions;
        this.storage = storage;
        this.access = access;
        this.projects = projects;
        this.users = users;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    /** Owner and editors; the version must be ready. Archived projects can still be shared. */
    @Transactional
    public CreatedShareLink create(UUID projectId, UUID versionId, UUID userId, @Nullable Integer validDays) {
        access.requireRole(projectId, userId, ProjectRole.EDITOR);
        TrackVersion version = versions.findByIdAndProjectId(versionId, projectId)
                .filter(TrackVersion::isConfirmed)
                .orElseThrow(() -> NotFoundException.of("version", versionId));
        version.requireReady();

        TokenGenerator.GeneratedToken token = tokens.generate();
        ShareLink link = links.save(ShareLink.builder()
                .projectId(projectId)
                .versionId(versionId)
                .tokenHash(token.hash())
                .createdBy(userId)
                .validDays(Objects.requireNonNullElse(validDays, ShareLink.DEFAULT_DAYS))
                .createdAt(clock.instant())
                .build());
        log.info(
                "Share link created linkId={} versionId={} expiresAt={}", link.getId(), versionId, link.getExpiresAt());
        return new CreatedShareLink(link.getId(), versionId, link.getExpiresAt(), token.value());
    }

    @Transactional
    public void revoke(UUID projectId, UUID linkId, UUID userId) {
        ProjectRole role = access.requireRole(projectId, userId, ProjectRole.LISTENER);
        ShareLink link = links.findByIdAndProjectId(linkId, projectId)
                .orElseThrow(() -> NotFoundException.of("share-link", linkId));
        link.revoke(userId, role == ProjectRole.OWNER, clock.instant());
        log.info("Share link revoked linkId={} projectId={}", linkId, projectId);
    }

    /**
     * No login: the token is the authorization. Unknown, expired and revoked links all answer the
     * same 404, so a visitor cannot tell them apart.
     */
    @Transactional(readOnly = true)
    public ListenView listen(String token) {
        Instant now = clock.instant();
        ShareLink link = links.findByTokenHash(tokens.hash(token))
                .filter(found -> tokens.matches(token, found.getTokenHash()))
                .filter(found -> found.isUsable(now))
                .orElseThrow(ShareLinkService::linkNotFound);
        TrackVersion version = versions.findById(link.getVersionId())
                .filter(found -> found.getStatus() == VersionStatus.READY)
                .orElseThrow(ShareLinkService::linkNotFound);
        String title = projects.findTitle(version.getProjectId()).orElseThrow(ShareLinkService::linkNotFound);
        String author = users.displayNames(Set.of(version.getUploadedBy()))
                .getOrDefault(version.getUploadedBy(), VersionView.UNKNOWN_USER);

        var ttl = properties.playback().urlTtl();
        return new ListenView(
                title,
                version.getNumber(),
                author,
                version.getDurationMs(),
                storage.presignDownload(version.getPreviewKey(), null, ttl),
                storage.presignDownload(version.getPeaksKey(), null, ttl));
    }

    private static NotFoundException linkNotFound() {
        return new NotFoundException("share-link-not-found", "This listening link does not exist or expired");
    }
}
