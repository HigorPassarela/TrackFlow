package dev.higor.trackflow.api.version;

/** Lifecycle of a track version. Transitions are enforced by the {@code TrackVersion} entity. */
public enum VersionStatus {
    PENDING_UPLOAD,
    UPLOADED,
    PROCESSING,
    READY,
    FAILED
}
