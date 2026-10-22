package dev.higor.trackflow.api.ableton.domain;

import dev.higor.trackflow.api.ableton.AbletonAnalyses.ProjectSnapshot;
import dev.higor.trackflow.api.ableton.AbletonAnalyses.Track;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * What changed from one Ableton project snapshot to another (design D10). A pure function: no
 * Spring, no database, so every rule is tested with plain records.
 *
 * <p>Tracks are paired by Ableton's own {@code Id}, which survives renames: the same id with another
 * name is a rename, never a removal followed by an addition.
 *
 * @param tempo {@code null} when the tempo did not change
 * @param changedTracks tracks present in both snapshots whose devices or plugins changed
 */
public record AbletonDiff(
        @Nullable TempoChange tempo,
        List<TrackRef> addedTracks,
        List<TrackRef> removedTracks,
        List<Rename> renamedTracks,
        List<TrackChange> changedTracks) {

    public AbletonDiff {
        addedTracks = List.copyOf(addedTracks);
        removedTracks = List.copyOf(removedTracks);
        renamedTracks = List.copyOf(renamedTracks);
        changedTracks = List.copyOf(changedTracks);
    }

    public static AbletonDiff between(ProjectSnapshot from, ProjectSnapshot to) {
        Map<String, Track> before = byId(from.tracks());
        Map<String, Track> after = byId(to.tracks());

        List<TrackRef> added = new ArrayList<>();
        List<Rename> renamed = new ArrayList<>();
        List<TrackChange> changed = new ArrayList<>();
        for (Track track : after.values()) {
            Track previous = before.get(track.id());
            if (previous == null) {
                added.add(TrackRef.of(track));
                continue;
            }
            if (!previous.name().equals(track.name())) {
                renamed.add(new Rename(track.id(), previous.name(), track.name()));
            }
            TrackChange change = TrackChange.of(previous, track);
            if (change.hasChanges()) {
                changed.add(change);
            }
        }
        List<TrackRef> removed = before.values().stream()
                .filter(track -> !after.containsKey(track.id()))
                .map(TrackRef::of)
                .toList();

        return new AbletonDiff(TempoChange.of(from.tempo(), to.tempo()), added, removed, renamed, changed);
    }

    private static Map<String, Track> byId(List<Track> tracks) {
        return tracks.stream()
                .collect(
                        Collectors.toMap(Track::id, Function.identity(), (first, second) -> first, LinkedHashMap::new));
    }

    /** Elements of {@code to} missing from {@code from}, in the order of {@code to}. */
    private static List<String> missingFrom(List<String> from, List<String> to) {
        Set<String> result = new LinkedHashSet<>(to);
        result.removeAll(from);
        return List.copyOf(result);
    }

    public record TempoChange(
            @Nullable Double from, @Nullable Double to) {

        static @Nullable TempoChange of(@Nullable Double from, @Nullable Double to) {
            return Objects.equals(from, to) ? null : new TempoChange(from, to);
        }
    }

    public record TrackRef(String id, String type, String name) {

        static TrackRef of(Track track) {
            return new TrackRef(track.id(), track.type(), track.name());
        }
    }

    public record Rename(String id, String from, String to) {}

    /** Changes inside a track that exists in both snapshots, named as it is in the newer one. */
    public record TrackChange(
            String id,
            String name,
            List<String> devicesAdded,
            List<String> devicesRemoved,
            List<String> pluginsAdded,
            List<String> pluginsRemoved) {

        public TrackChange {
            devicesAdded = List.copyOf(devicesAdded);
            devicesRemoved = List.copyOf(devicesRemoved);
            pluginsAdded = List.copyOf(pluginsAdded);
            pluginsRemoved = List.copyOf(pluginsRemoved);
        }

        static TrackChange of(Track before, Track after) {
            return new TrackChange(
                    after.id(),
                    after.name(),
                    missingFrom(before.devices(), after.devices()),
                    missingFrom(after.devices(), before.devices()),
                    missingFrom(before.plugins(), after.plugins()),
                    missingFrom(after.plugins(), before.plugins()));
        }

        boolean hasChanges() {
            return !devicesAdded.isEmpty()
                    || !devicesRemoved.isEmpty()
                    || !pluginsAdded.isEmpty()
                    || !pluginsRemoved.isEmpty();
        }
    }
}
