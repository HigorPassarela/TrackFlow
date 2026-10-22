package dev.higor.trackflow.worker.audio;

import dev.higor.trackflow.worker.process.ProcessRunner;
import dev.higor.trackflow.worker.process.ProcessRunner.ProcessResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** {@link Transcoder} backed by ffmpeg. {@code -map 0:a:0} picks the first audio stream of videos too. */
@Component
class FfmpegTranscoder implements Transcoder {

    private final ProcessRunner runner;
    private final AudioProperties properties;

    FfmpegTranscoder(ProcessRunner runner, AudioProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    @Override
    public void decodeToPcm(Path source, Path target) {
        ProcessResult result = run(
                source,
                List.of("-ac", "1", "-ar", String.valueOf(PCM_SAMPLE_RATE), "-f", "s16le", "-c:a", "pcm_s16le"),
                target);
        if (!result.succeeded()) {
            // ffprobe already accepted the file, so a decoding error means damaged content.
            throw new InvalidMediaException(InvalidMediaException.UNREADABLE_FILE, "ffmpeg could not decode the audio");
        }
    }

    @Override
    public void encodePreview(Path source, Path target) {
        ProcessResult result = run(
                source, List.of("-c:a", "libmp3lame", "-b:a", "192k", "-ar", "44100", "-ac", "2", "-f", "mp3"), target);
        if (!result.succeeded()) {
            throw new IllegalStateException("ffmpeg could not encode the preview: " + result.stderr());
        }
    }

    private ProcessResult run(Path source, List<String> outputOptions, Path target) {
        Path absolute = source.toAbsolutePath();
        List<String> command = new ArrayList<>(List.of(
                properties.ffmpeg(),
                "-nostdin",
                "-hide_banner",
                "-v",
                "error",
                "-y",
                "-i",
                absolute.toString(),
                "-map",
                "0:a:0",
                "-vn"));
        command.addAll(outputOptions);
        command.add(target.toAbsolutePath().toString());
        return runner.run(command, absolute.getParent(), properties.stepTimeout());
    }
}
