package dev.higor.trackflow.worker.ableton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.higor.trackflow.contracts.AbletonAnalyzed.Track;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AbletonProjectReaderTest {

    private final AbletonProjectReader reader = new AbletonProjectReader();

    @Test
    @DisplayName("Scenario: tech house project")
    void readsTempoTracksAndPluginsOfALive12Project() {
        AbletonProject project = read(AbletonFixtures.als("live12-tech-house.xml"));

        assertThat(project.tempo()).isEqualTo(126.0);
        assertThat(project.timeSignature()).isEqualTo("4/4");
        assertThat(project.tracks())
                .extracting(Track::id, Track::type, Track::name)
                .containsExactly(
                        tuple("8", "audio", "Kick"), tuple("12", "midi", "Bass"), tuple("15", "audio", "Vocal Chop"));
        assertThat(trackNamed(project, "Bass").plugins()).containsExactly("Serum");
        assertThat(trackNamed(project, "Bass").devices()).containsExactly("PluginDevice", "AutoFilter");
        assertThat(trackNamed(project, "Kick").devices()).containsExactly("Compressor2");
    }

    @Test
    @DisplayName("Scenario: Live 12 project")
    void identifiesLive12AsAVerifiedVersion() {
        AbletonProject project = read(AbletonFixtures.als("live12-tech-house.xml"));

        assertThat(project.liveVersion()).isEqualTo("Ableton Live 12.4.6");
        assertThat(project.verifiedVersion()).isTrue();
    }

    @Test
    void readsLive11MasterTrackGroupsReturnsAndPluginsInsideRacks() {
        AbletonProject project = read(AbletonFixtures.als("live11-group-and-rack.xml"));

        assertThat(project.verifiedVersion()).isTrue();
        assertThat(project.tempo()).isEqualTo(124.5);
        assertThat(project.timeSignature()).isEqualTo("6/8");
        assertThat(project.tracks())
                .extracting(Track::type, Track::name)
                .containsExactly(tuple("group", "Drums"), tuple("midi", "Lead"), tuple("return", "A-Reverb"));
        assertThat(trackNamed(project, "Drums").devices()).containsExactly("AudioEffectGroupDevice");
        assertThat(trackNamed(project, "Drums").plugins()).containsExactly("Decapitator");
        assertThat(trackNamed(project, "Lead").plugins()).containsExactly("Diva");
    }

    @Test
    void marksOtherLiveVersionsAsNotVerified() {
        byte[] live10 = AbletonFixtures.gzip("""
                <Ableton Creator="Ableton Live 10.1.30"><LiveSet><Tracks /></LiveSet></Ableton>
                """.getBytes());

        AbletonProject project = read(live10);

        assertThat(project.liveVersion()).isEqualTo("Ableton Live 10.1.30");
        assertThat(project.verifiedVersion()).isFalse();
    }

    @Test
    @DisplayName("Scenario: file with an external reference")
    void refusesExternalEntitiesWithoutResolvingThem() {
        byte[] hostile = AbletonFixtures.als("external-entity.xml");

        assertThatThrownBy(() -> read(hostile))
                .isInstanceOfSatisfying(
                        InvalidProjectException.class,
                        error -> assertThat(error.reason()).isEqualTo(InvalidProjectException.INVALID_PROJECT_FILE));
    }

    @Test
    @DisplayName("Scenario: decompression bomb")
    void stopsReadingWhenTheProjectExpandsBeyondTheLimit() {
        byte[] bomb = AbletonFixtures.decompressionBomb();

        assertThat(bomb.length).isLessThan(2 * 1024 * 1024);
        assertThatThrownBy(() -> read(bomb))
                .isInstanceOfSatisfying(
                        InvalidProjectException.class,
                        error -> assertThat(error.reason()).isEqualTo(InvalidProjectException.PROJECT_FILE_TOO_LARGE));
    }

    @Test
    @DisplayName("Scenario: corrupted project")
    void refusesAFileThatIsNotGzip() {
        assertThatThrownBy(() -> read("not a project".getBytes()))
                .isInstanceOfSatisfying(
                        InvalidProjectException.class,
                        error -> assertThat(error.reason()).isEqualTo(InvalidProjectException.INVALID_PROJECT_FILE));
    }

    @Test
    void refusesXmlThatIsNotAnAbletonProject() {
        assertThatThrownBy(() -> read(AbletonFixtures.gzip("<svg />".getBytes())))
                .isInstanceOf(InvalidProjectException.class);
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({"201, 4/4", "200, 3/4", "302, 6/8", "303, 7/8", "102, 4/2"})
    void decodesLiveTimeSignatures(int encoded, String expected) {
        assertThat(AbletonProjectReader.timeSignature(encoded)).isEqualTo(expected);
    }

    private AbletonProject read(byte[] als) {
        return reader.read(new ByteArrayInputStream(als));
    }

    private static Track trackNamed(AbletonProject project, String name) {
        return project.tracks().stream()
                .filter(track -> track.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
