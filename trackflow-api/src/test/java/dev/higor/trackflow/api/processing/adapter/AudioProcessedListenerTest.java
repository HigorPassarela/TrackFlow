package dev.higor.trackflow.api.processing.adapter;

import static org.mockito.BDDMockito.then;

import dev.higor.trackflow.api.version.ReadyMedia;
import dev.higor.trackflow.api.version.TrackVersions;
import dev.higor.trackflow.contracts.AudioProcessed;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AudioProcessedListenerTest {

    private static final UUID MESSAGE = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant AT = Instant.parse("2026-10-22T10:00:00Z");

    @Mock
    private TrackVersions versions;

    @Test
    void completesTheVersionWithTheJobOfTheResult() {
        var listener = new AudioProcessedListener(versions);

        listener.on(new AudioProcessed.Succeeded(
                MESSAGE, JOB, VERSION, AT, "preview.mp3", "peaks.json", 360_000L, 124.0, -9.4, 0.8));

        then(versions)
                .should()
                .completeProcessing(
                        VERSION, JOB, new ReadyMedia("preview.mp3", "peaks.json", 360_000L, 124.0, -9.4, 0.8));
    }

    @Test
    void failsTheVersionWithTheReasonOfTheResult() {
        var listener = new AudioProcessedListener(versions);

        listener.on(new AudioProcessed.Failed(MESSAGE, JOB, VERSION, AT, "no-audio-stream"));

        then(versions).should().failProcessing(VERSION, JOB, "no-audio-stream");
    }
}
