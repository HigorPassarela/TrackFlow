package dev.higor.trackflow.worker.audio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.higor.trackflow.worker.process.ProcessRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Media that Java cannot write by itself (videos, compressed audio), generated with ffmpeg. */
public final class FfmpegTestFiles {

    private static final ProcessRunner RUNNER = new ProcessRunner();

    private FfmpegTestFiles() {}

    /** Runs {@code ffmpeg -y <arguments> <target>} and returns the target. */
    public static Path generate(Path target, String... arguments) {
        List<String> command = new ArrayList<>(List.of("ffmpeg", "-nostdin", "-hide_banner", "-v", "error", "-y"));
        command.addAll(List.of(arguments));
        command.add(target.toAbsolutePath().toString());
        var result = RUNNER.run(command, target.toAbsolutePath().getParent(), Duration.ofMinutes(2));
        assertThat(result.succeeded()).as(result.stderr()).isTrue();
        return target;
    }

    /** A short video with a picture and no audio stream at all, like a muted screen recording. */
    public static Path videoWithoutAudio(Path target) {
        return generate(target, "-f", "lavfi", "-i", "testsrc=size=160x120:rate=10", "-t", "2", "-c:v", "mpeg4");
    }

    /** A screen recording: a picture plus the given WAV as its audio stream. */
    public static Path videoWithAudio(Path target, Path wav) {
        return generate(
                target,
                "-f",
                "lavfi",
                "-i",
                "testsrc=size=160x120:rate=10",
                "-i",
                wav.toAbsolutePath().toString(),
                "-shortest",
                "-c:v",
                "mpeg4",
                "-c:a",
                "aac");
    }
}
