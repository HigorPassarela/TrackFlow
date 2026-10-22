package dev.higor.trackflow.worker.audio;

import dev.higor.trackflow.worker.process.ProcessRunner;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;

/** The real ffmpeg adapters, wired by hand. */
final class FfmpegTestSupport {

    static final AudioProperties PROPERTIES =
            new AudioProperties(Duration.ofMinutes(20), Duration.ofMinutes(5), "ffmpeg", "ffprobe");
    static final ProcessRunner RUNNER = new ProcessRunner();
    static final JsonMapper JSON = JsonMapper.builder().build();

    private FfmpegTestSupport() {}

    static MediaProbe probe() {
        return new FfprobeMediaProbe(RUNNER, JSON, PROPERTIES);
    }

    static Transcoder transcoder() {
        return new FfmpegTranscoder(RUNNER, PROPERTIES);
    }

    static LoudnessMeter loudnessMeter() {
        return new FfmpegLoudnessMeter(RUNNER, PROPERTIES);
    }
}
