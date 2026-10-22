package dev.higor.trackflow.api.ableton.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.higor.trackflow.api.ableton.AbletonAnalyses.ProjectSnapshot;
import dev.higor.trackflow.api.ableton.AbletonAnalyses.Track;
import dev.higor.trackflow.api.ableton.domain.AbletonDiff.Rename;
import dev.higor.trackflow.api.ableton.domain.AbletonDiff.TempoChange;
import dev.higor.trackflow.api.ableton.domain.AbletonDiff.TrackChange;
import dev.higor.trackflow.api.ableton.domain.AbletonDiff.TrackRef;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbletonDiffTest {

    private static final Track KICK = track("8", "audio", "Kick", List.of("Compressor2"), List.of());
    private static final Track BASS = track("12", "midi", "Bass", List.of("PluginDevice"), List.of("Serum"));

    @Test
    @DisplayName("Scenario: evolution from v3 to v4")
    void listsAddedTracksRemovedPluginAndTempoChange() {
        ProjectSnapshot v3 = snapshot(125.0, KICK, BASS);
        ProjectSnapshot v4 = snapshot(
                126.0,
                KICK,
                track("12", "midi", "Bass", List.of("Operator"), List.of()),
                track("15", "audio", "Vocal Chop", List.of(), List.of()),
                track("16", "audio", "Riser", List.of(), List.of()));

        AbletonDiff diff = AbletonDiff.between(v3, v4);

        assertThat(diff.tempo()).isEqualTo(new TempoChange(125.0, 126.0));
        assertThat(diff.addedTracks()).extracting(TrackRef::name).containsExactly("Vocal Chop", "Riser");
        assertThat(diff.removedTracks()).isEmpty();
        assertThat(diff.changedTracks()).singleElement().satisfies(change -> {
            assertThat(change.name()).isEqualTo("Bass");
            assertThat(change.pluginsRemoved()).containsExactly("Serum");
            assertThat(change.devicesAdded()).containsExactly("Operator");
            assertThat(change.devicesRemoved()).containsExactly("PluginDevice");
        });
    }

    @Test
    @DisplayName("Scenario: renamed track")
    void sameIdWithAnotherNameIsARename() {
        ProjectSnapshot v3 = snapshot(126.0, KICK, BASS);
        ProjectSnapshot v4 =
                snapshot(126.0, KICK, track("12", "midi", "Sub Bass", List.of("PluginDevice"), List.of("Serum")));

        AbletonDiff diff = AbletonDiff.between(v3, v4);

        assertThat(diff.renamedTracks()).containsExactly(new Rename("12", "Bass", "Sub Bass"));
        assertThat(diff.addedTracks()).isEmpty();
        assertThat(diff.removedTracks()).isEmpty();
        assertThat(diff.changedTracks()).isEmpty();
    }

    @Test
    void listsRemovedTracks() {
        AbletonDiff diff = AbletonDiff.between(snapshot(126.0, KICK, BASS), snapshot(126.0, KICK));

        assertThat(diff.removedTracks()).containsExactly(new TrackRef("12", "midi", "Bass"));
    }

    @Test
    void listsPluginsAddedToATrack() {
        Track bassWithOtt = track("12", "midi", "Bass", List.of("PluginDevice"), List.of("Serum", "OTT"));

        AbletonDiff diff = AbletonDiff.between(snapshot(126.0, BASS), snapshot(126.0, bassWithOtt));

        assertThat(diff.changedTracks()).extracting(TrackChange::pluginsAdded).containsExactly(List.of("OTT"));
    }

    @Test
    void identicalSnapshotsHaveNoChanges() {
        AbletonDiff diff = AbletonDiff.between(snapshot(126.0, KICK, BASS), snapshot(126.0, KICK, BASS));

        assertThat(diff.tempo()).isNull();
        assertThat(diff.addedTracks()).isEmpty();
        assertThat(diff.removedTracks()).isEmpty();
        assertThat(diff.renamedTracks()).isEmpty();
        assertThat(diff.changedTracks()).isEmpty();
    }

    private static ProjectSnapshot snapshot(double tempo, Track... tracks) {
        return new ProjectSnapshot("Ableton Live 12.4.6", true, tempo, "4/4", List.of(tracks));
    }

    private static Track track(String id, String type, String name, List<String> devices, List<String> plugins) {
        return new Track(id, type, name, devices, plugins);
    }
}
