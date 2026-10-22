/**
 * Snapshots of the Ableton Live projects attached to versions and the diff between two of them.
 * The analysis has its own lifecycle, independent of the version: a broken {@code .als} never stops
 * a version from becoming ready.
 *
 * <p>Public API: {@link dev.higor.trackflow.api.ableton.AbletonAnalyses} and its records.
 */
@ApplicationModule(displayName = "Ableton project analysis")
package dev.higor.trackflow.api.ableton;

import org.springframework.modulith.ApplicationModule;
