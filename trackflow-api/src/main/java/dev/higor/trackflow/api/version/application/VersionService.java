package dev.higor.trackflow.api.version.application;

import static java.util.stream.Collectors.toSet;

import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectActivity;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.InvalidInputException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.web.PageResponse;
import dev.higor.trackflow.api.storage.ObjectStorage;
import dev.higor.trackflow.api.storage.SignedRequest;
import dev.higor.trackflow.api.version.VersionConfirmed;
import dev.higor.trackflow.api.version.VersionReprocessRequested;
import dev.higor.trackflow.api.version.VersionStatusChanged;
import dev.higor.trackflow.api.version.domain.MediaFormat;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import dev.higor.trackflow.api.version.domain.VersionNumbers;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

/**
 * Use cases of track versions. Files never pass through here: the service signs requests that the
 * client sends straight to the storage, then checks the result on {@code confirm}.
 */
@Service
public class VersionService {

    private static final Logger log = LoggerFactory.getLogger(VersionService.class);

    static final String ABLETON_EXTENSION = "als";
    static final String ABLETON_CONTENT_TYPE = "application/octet-stream";

    private final TrackVersionRepository versions;
    private final VersionNumbers numbers;
    private final ObjectStorage storage;
    private final ProjectAccess access;
    private final ProjectActivity activity;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final VersionProperties properties;
    private final Clock clock;

    public VersionService(
            TrackVersionRepository versions,
            VersionNumbers numbers,
            ObjectStorage storage,
            ProjectAccess access,
            ProjectActivity activity,
            UserDirectory users,
            ApplicationEventPublisher events,
            VersionProperties properties,
            Clock clock) {
        this.versions = versions;
        this.numbers = numbers;
        this.storage = storage;
        this.access = access;
        this.activity = activity;
        this.users = users;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public UploadTicket requestUpload(RequestUpload command) {
        access.requireRoleOnActiveProject(command.projectId(), command.userId(), ProjectRole.EDITOR);
        MediaFormat format = requireAcceptedMedia(command);
        if (command.ableton() != null) {
            requireAcceptedAbleton(command.ableton());
        }

        TrackVersion version = TrackVersion.builder()
                .projectId(command.projectId())
                .uploadedBy(command.userId())
                .originalFileName(command.fileName())
                .contentType(command.contentType())
                .sizeBytes(command.sizeBytes())
                .alsFileName(command.ableton() != null ? command.ableton().fileName() : null)
                .alsSizeBytes(command.ableton() != null ? command.ableton().sizeBytes() : null)
                .title(command.title())
                .notes(command.notes())
                .createdAt(clock.instant())
                .build();
        String prefix = "projects/%s/versions/%s/".formatted(command.projectId(), version.getId());
        version.assignStorageKeys(
                prefix + "source." + format.storageExtension(),
                command.ableton() != null ? prefix + "project.als" : null);
        versions.save(version);

        var ttl = properties.upload().urlTtl();
        SignedRequest source =
                storage.presignUpload(version.getSourceKey(), command.contentType(), command.sizeBytes(), ttl);
        SignedRequest ableton = version.getAlsKey() == null
                ? null
                : storage.presignUpload(version.getAlsKey(), ABLETON_CONTENT_TYPE, version.getAlsSizeBytes(), ttl);
        log.info(
                "Upload requested versionId={} projectId={} sizeBytes={} ableton={}",
                version.getId(),
                command.projectId(),
                command.sizeBytes(),
                ableton != null);
        return new UploadTicket(view(version), source, ableton);
    }

    /**
     * Gives the version its number once every file is in the storage with the declared size.
     * Repeating the call returns the confirmed version without a new number or a new job.
     */
    @Transactional
    public VersionView confirm(UUID projectId, UUID versionId, UUID userId) {
        access.requireRoleOnActiveProject(projectId, userId, ProjectRole.EDITOR);
        TrackVersion version = versions.findByIdAndProjectId(versionId, projectId)
                .orElseThrow(() -> NotFoundException.of("version", versionId));
        if (version.isConfirmed()) {
            return view(version);
        }
        requireStored(version.getSourceKey(), version.getSizeBytes());
        if (version.getAlsKey() != null) {
            requireStored(version.getAlsKey(), version.getAlsSizeBytes());
        }

        Instant now = clock.instant();
        version.confirm(numbers.next(projectId), now);
        activity.record(projectId);
        events.publishEvent(new VersionStatusChanged(version.getId(), projectId, version.getStatus(), now));
        events.publishEvent(new VersionConfirmed(
                version.getId(), projectId, version.getNumber(), version.getSourceKey(), version.getAlsKey()));
        log.info("Version confirmed versionId={} projectId={} number={}", versionId, projectId, version.getNumber());
        return view(version);
    }

    @Transactional(readOnly = true)
    public PageResponse<VersionView> history(UUID projectId, UUID userId, int page, int size) {
        access.requireRole(projectId, userId, ProjectRole.LISTENER);
        Page<TrackVersion> history = versions.findHistory(projectId, PageRequest.of(page, size));
        Map<UUID, String> names = users.displayNames(
                history.getContent().stream().map(TrackVersion::getUploadedBy).collect(toSet()));
        return PageResponse.of(history, version -> VersionView.of(version, names));
    }

    @Transactional(readOnly = true)
    public VersionView get(UUID projectId, UUID versionId, UUID userId) {
        access.requireRole(projectId, userId, ProjectRole.LISTENER);
        return view(requireConfirmed(projectId, versionId));
    }

    @Transactional
    public VersionView edit(EditVersion command) {
        access.requireRoleOnActiveProject(command.projectId(), command.userId(), ProjectRole.EDITOR);
        TrackVersion version = requireConfirmed(command.projectId(), command.versionId());
        Instant now = clock.instant();
        version.editDetails(
                command.title() != null ? command.title() : version.getTitle(),
                command.notes() != null ? command.notes() : version.getNotes(),
                now);
        if (command.bpmOverride() != null) {
            version.correctBpm(command.bpmOverride(), now);
        }
        log.info("Version edited versionId={} projectId={}", command.versionId(), command.projectId());
        return view(version);
    }

    /** Preview and peaks of a ready version; the storage answers Range requests, so seeking works. */
    @Transactional(readOnly = true)
    public MediaLinks playback(UUID projectId, UUID versionId, UUID userId) {
        access.requireRole(projectId, userId, ProjectRole.LISTENER);
        TrackVersion version = requireConfirmed(projectId, versionId);
        version.requireReady();
        var ttl = properties.playback().urlTtl();
        return new MediaLinks(
                storage.presignDownload(version.getPreviewKey(), null, ttl),
                storage.presignDownload(version.getPeaksKey(), null, ttl));
    }

    /** Originals are for owner and editors only; listeners get 403. */
    @Transactional(readOnly = true)
    public MediaLinks download(UUID projectId, UUID versionId, UUID userId) {
        access.requireRole(projectId, userId, ProjectRole.EDITOR);
        TrackVersion version = requireConfirmed(projectId, versionId);
        var ttl = properties.playback().urlTtl();
        SignedRequest original = storage.presignDownload(version.getSourceKey(), version.getOriginalFileName(), ttl);
        SignedRequest ableton = version.getAlsKey() == null
                ? null
                : storage.presignDownload(version.getAlsKey(), version.getAlsFileName(), ttl);
        return new MediaLinks(original, ableton);
    }

    @Transactional
    public VersionView reprocess(UUID projectId, UUID versionId, UUID userId) {
        access.requireRoleOnActiveProject(projectId, userId, ProjectRole.EDITOR);
        TrackVersion version = requireConfirmed(projectId, versionId);
        Instant now = clock.instant();
        version.reprocess(now);
        events.publishEvent(new VersionStatusChanged(version.getId(), projectId, version.getStatus(), now));
        events.publishEvent(new VersionReprocessRequested(version.getId(), projectId, version.getSourceKey()));
        log.info("Version reprocess requested versionId={} projectId={}", versionId, projectId);
        return view(version);
    }

    private MediaFormat requireAcceptedMedia(RequestUpload command) {
        MediaFormat format = MediaFormat.ofFileName(command.fileName())
                .filter(candidate -> candidate.accepts(command.contentType()))
                .orElseThrow(() -> new InvalidInputException(
                        "unsupported-format", "Accepted formats: " + MediaFormat.acceptedList()));
        long max = properties.upload().maxFileSize().toBytes();
        if (command.sizeBytes() > max) {
            throw new InvalidInputException(
                    "file-too-large",
                    "Files are limited to " + readable(properties.upload().maxFileSize()));
        }
        return format;
    }

    private void requireAcceptedAbleton(RequestUpload.AbletonAttachment ableton) {
        if (!MediaFormat.extensionOf(ableton.fileName()).equals(ABLETON_EXTENSION)) {
            throw new InvalidInputException(
                    "ableton-invalid-extension", "Only Ableton Live project files (.als) can be attached");
        }
        if (ableton.sizeBytes() > properties.upload().maxAbletonFileSize().toBytes()) {
            throw new InvalidInputException(
                    "ableton-file-too-large",
                    "Ableton projects are limited to "
                            + readable(properties.upload().maxAbletonFileSize()));
        }
    }

    /** {@code 1 GB}, {@code 50 MB}: limits as people read them, for error messages. */
    static String readable(DataSize size) {
        if (size.toGigabytes() > 0 && size.toBytes() % DataSize.ofGigabytes(1).toBytes() == 0) {
            return size.toGigabytes() + " GB";
        }
        return size.toMegabytes() + " MB";
    }

    private void requireStored(String key, @Nullable Long declaredSize) {
        var stored = storage.head(key)
                .orElseThrow(() -> new BusinessRuleException("upload-missing", "The file was not uploaded yet"));
        if (declaredSize == null || stored.sizeBytes() != declaredSize) {
            throw new BusinessRuleException(
                    "upload-size-mismatch", "The uploaded file does not have the declared size");
        }
    }

    /** Pending uploads are invisible: for the API they do not exist until confirmed. */
    private TrackVersion requireConfirmed(UUID projectId, UUID versionId) {
        return versions.findByIdAndProjectId(versionId, projectId)
                .filter(TrackVersion::isConfirmed)
                .orElseThrow(() -> NotFoundException.of("version", versionId));
    }

    private VersionView view(TrackVersion version) {
        return VersionView.of(version, users.displayNames(Set.of(version.getUploadedBy())));
    }
}
