package dev.higor.trackflow.api.version.domain;

import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.version.ReadyMedia;
import dev.higor.trackflow.api.version.VersionStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * One bounce of a track. The state machine (design D8):
 *
 * <pre>
 * PENDING_UPLOAD --confirm--> UPLOADED --startProcessing--> PROCESSING --complete--> READY
 *                                 ^                              |
 *                                 |                              +--fail--> FAILED
 *                                 +--------------reprocess-------------------+
 * </pre>
 *
 * Invalid transitions throw {@link BusinessRuleException}. Processing results are the exception:
 * a result for another job, or for a version that already finished, is ignored and reported as
 * {@code false}, because the broker may deliver it twice or late.
 */
@Entity
@Table(name = "track_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class TrackVersion {

    public static final int MAX_TITLE_LENGTH = 80;
    public static final int MAX_NOTES_LENGTH = 2000;
    public static final double MIN_BPM = 40;
    public static final double MAX_BPM = 250;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Version
    private long rowVersion;

    private UUID projectId;

    /** Assigned on confirmation; pending uploads never consume a number. */
    private @Nullable Integer number;

    @Enumerated(EnumType.STRING)
    private VersionStatus status;

    private @Nullable String title;

    private @Nullable String notes;

    private UUID uploadedBy;

    private String originalFileName;

    private String contentType;

    private long sizeBytes;

    /** Not {@code @Nullable}: only unset between the first save and {@link #assignStorageKeys}. */
    private String sourceKey;

    private @Nullable String alsKey;

    private @Nullable String alsFileName;

    private @Nullable Long alsSizeBytes;

    private @Nullable String previewKey;

    private @Nullable String peaksKey;

    private @Nullable Long durationMs;

    private @Nullable Double bpmEstimated;

    private @Nullable Double bpmOverride;

    private @Nullable Double loudnessLufs;

    private @Nullable Double truePeakDbtp;

    private @Nullable String failureReason;

    private @Nullable UUID processingJobId;

    private Instant createdAt;

    private @Nullable Instant confirmedAt;

    private Instant updatedAt;

    /**
     * The storage keys depend on the id, which Hibernate generates on {@code persist}: save the
     * version first, then call {@link #assignStorageKeys}. The row is only inserted at flush, so the
     * keys are always there when it reaches the database.
     */
    @Builder
    private TrackVersion(
            UUID projectId,
            UUID uploadedBy,
            String originalFileName,
            String contentType,
            long sizeBytes,
            @Nullable String alsFileName,
            @Nullable Long alsSizeBytes,
            @Nullable String title,
            @Nullable String notes,
            Instant createdAt) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Uploads must have content");
        }
        if ((alsFileName == null) != (alsSizeBytes == null)) {
            throw new IllegalArgumentException("Ableton attachment needs file name and size together");
        }
        this.projectId = projectId;
        this.uploadedBy = uploadedBy;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.alsFileName = alsFileName;
        this.alsSizeBytes = alsSizeBytes;
        this.status = VersionStatus.PENDING_UPLOAD;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        applyDetails(title, notes);
    }

    /**
     * Deterministic keys under the version's id, so reprocessing overwrites the same objects.
     * Assigned exactly once, right after the first save.
     */
    public void assignStorageKeys(String newSourceKey, @Nullable String newAlsKey) {
        if (sourceKey != null) {
            throw new IllegalStateException("Storage keys are already assigned for version " + id);
        }
        if ((newAlsKey == null) != (alsFileName == null)) {
            throw new IllegalArgumentException("Ableton key must match the declared attachment");
        }
        sourceKey = newSourceKey;
        alsKey = newAlsKey;
    }

    public boolean hasAbletonProject() {
        return alsKey != null;
    }

    public boolean isConfirmed() {
        return status != VersionStatus.PENDING_UPLOAD;
    }

    /** The BPM to show: the one a member informed, otherwise the estimate. */
    public @Nullable Double effectiveBpm() {
        return bpmOverride != null ? bpmOverride : bpmEstimated;
    }

    public void confirm(int assignedNumber, Instant at) {
        requireStatus(VersionStatus.PENDING_UPLOAD, "version-already-confirmed", "The version was already confirmed");
        number = assignedNumber;
        status = VersionStatus.UPLOADED;
        confirmedAt = at;
        updatedAt = at;
    }

    public void startProcessing(UUID jobId, Instant at) {
        requireStatus(VersionStatus.UPLOADED, "version-not-uploaded", "Only uploaded versions can be processed");
        status = VersionStatus.PROCESSING;
        processingJobId = jobId;
        updatedAt = at;
    }

    /** @return whether the result was applied; {@code false} for duplicated or stale results */
    public boolean complete(UUID jobId, ReadyMedia media, Instant at) {
        if (!isProcessing(jobId)) {
            return false;
        }
        status = VersionStatus.READY;
        previewKey = media.previewKey();
        peaksKey = media.peaksKey();
        durationMs = media.durationMs();
        bpmEstimated = media.bpmEstimated();
        loudnessLufs = media.loudnessLufs();
        truePeakDbtp = media.truePeakDbtp();
        failureReason = null;
        updatedAt = at;
        return true;
    }

    /** @return whether the failure was applied; {@code false} for duplicated or stale results */
    public boolean fail(UUID jobId, String reason, Instant at) {
        if (!isProcessing(jobId)) {
            return false;
        }
        status = VersionStatus.FAILED;
        failureReason = reason;
        updatedAt = at;
        return true;
    }

    /** Back to {@code UPLOADED}; the next {@link #startProcessing} brings a new job id. */
    public void reprocess(Instant at) {
        requireStatus(VersionStatus.FAILED, "version-not-failed", "Only failed versions can be reprocessed");
        status = VersionStatus.UPLOADED;
        processingJobId = null;
        updatedAt = at;
    }

    public void editDetails(@Nullable String newTitle, @Nullable String newNotes, Instant at) {
        applyDetails(newTitle, newNotes);
        updatedAt = at;
    }

    public void correctBpm(@Nullable Double bpm, Instant at) {
        if (bpm != null && (bpm < MIN_BPM || bpm > MAX_BPM)) {
            throw new IllegalArgumentException("BPM must be between 40 and 250");
        }
        bpmOverride = bpm;
        updatedAt = at;
    }

    public void requireReady() {
        requireStatus(VersionStatus.READY, "version-not-ready", "The version is not ready yet");
    }

    private boolean isProcessing(UUID jobId) {
        return status == VersionStatus.PROCESSING && Objects.equals(processingJobId, jobId);
    }

    private void requireStatus(VersionStatus expected, String code, String message) {
        if (status != expected) {
            throw new BusinessRuleException(code, message);
        }
    }

    private void applyDetails(@Nullable String newTitle, @Nullable String newNotes) {
        title = blankToNull(newTitle, MAX_TITLE_LENGTH);
        notes = blankToNull(newNotes, MAX_NOTES_LENGTH);
    }

    private static @Nullable String blankToNull(@Nullable String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > max) {
            throw new IllegalArgumentException("Text longer than " + max + " characters");
        }
        return stripped;
    }
}
