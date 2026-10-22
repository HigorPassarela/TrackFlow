package dev.higor.trackflow.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Round trip with a plain Jackson 3 mapper, the same library both apps use on the wire. If a record
 * stops deserializing (renamed field, missing subtype), the other side would fail in production.
 */
class ContractSerializationTest {

    private static final UUID MESSAGE = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant AT = Instant.parse("2026-10-22T10:00:00.123Z");

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void processAudioJobRoundTrips() {
        var job = new ProcessAudioJob(MESSAGE, JOB, VERSION, PROJECT, "projects/p/versions/v/source.wav", AT);

        assertThat(roundTrip(job, ProcessAudioJob.class)).isEqualTo(job);
    }

    @Test
    void analyzeAbletonProjectJobRoundTrips() {
        var job = new AnalyzeAbletonProjectJob(MESSAGE, JOB, VERSION, PROJECT, "projects/p/versions/v/project.als", AT);

        assertThat(roundTrip(job, AnalyzeAbletonProjectJob.class)).isEqualTo(job);
    }

    @Test
    void audioProcessedSuccessRoundTripsThroughTheSealedType() {
        AudioProcessed result = new AudioProcessed.Succeeded(
                MESSAGE, JOB, VERSION, AT, "preview.mp3", "peaks.json", 360_000L, null, -9.4, 0.8);

        assertThat(roundTrip(result, AudioProcessed.class)).isEqualTo(result);
    }

    @Test
    void audioProcessedFailureRoundTripsThroughTheSealedType() {
        AudioProcessed result = new AudioProcessed.Failed(MESSAGE, JOB, VERSION, AT, "no-audio-stream");

        assertThat(roundTrip(result, AudioProcessed.class)).isEqualTo(result);
    }

    @Test
    void abletonAnalyzedSuccessRoundTripsWithTracks() {
        AbletonAnalyzed result = new AbletonAnalyzed.Succeeded(
                MESSAGE,
                JOB,
                VERSION,
                AT,
                "Ableton Live 12.1.5",
                true,
                126.0,
                "4/4",
                List.of(new AbletonAnalyzed.Track("12", "midi", "Bass", List.of("Eq8"), List.of("Serum"))));

        assertThat(roundTrip(result, AbletonAnalyzed.class)).isEqualTo(result);
    }

    @Test
    void abletonAnalyzedFailureRoundTrips() {
        AbletonAnalyzed result = new AbletonAnalyzed.Failed(MESSAGE, JOB, VERSION, AT, "project-file-too-large");

        assertThat(roundTrip(result, AbletonAnalyzed.class)).isEqualTo(result);
    }

    @Test
    void sealedResultsCarryALogicalOutcomeNameInsteadOfAClassName() {
        AudioProcessed result = new AudioProcessed.Failed(MESSAGE, JOB, VERSION, AT, "unreadable-file");

        String written = json.writeValueAsString(result);

        assertThat(written).contains("\"outcome\":\"failed\"").doesNotContain("dev.higor");
    }

    @Test
    void writesInstantsAsIsoText() {
        var job = new ProcessAudioJob(MESSAGE, JOB, VERSION, PROJECT, "key", AT);

        assertThat(json.writeValueAsString(job)).contains("\"occurredAt\":\"2026-10-22T10:00:00.123Z\"");
    }

    private <T> T roundTrip(T value, Class<T> type) {
        return json.readValue(json.writeValueAsString(value), type);
    }
}
