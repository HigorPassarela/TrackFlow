/**
 * Track versions: direct uploads to the storage, numbering, history, playback, download, share
 * links and the real-time status channel.
 *
 * <p>Public API: {@link dev.higor.trackflow.api.version.TrackVersions}, the events {@link
 * dev.higor.trackflow.api.version.VersionConfirmed} and {@link
 * dev.higor.trackflow.api.version.VersionReprocessRequested}, and their records. Everything in
 * sub-packages is internal.
 */
@ApplicationModule(displayName = "Track versions")
package dev.higor.trackflow.api.version;

import org.springframework.modulith.ApplicationModule;
