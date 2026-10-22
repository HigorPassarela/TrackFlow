package dev.higor.trackflow.worker.audio;

import dev.higor.trackflow.worker.process.ProcessRunner;
import dev.higor.trackflow.worker.process.ProcessRunner.ProcessResult;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * {@link LoudnessMeter} backed by ffmpeg's {@code ebur128} filter with true peak (design D7).
 *
 * <p>The filter has no structured output for its final summary, so the summary block at the end of
 * stderr is parsed. Only the text after {@code Summary:} is read, so the per-frame log never matches.
 */
@Component
class FfmpegLoudnessMeter implements LoudnessMeter {

    private static final Pattern INTEGRATED = Pattern.compile("\\bI:\\s+(-?(?:\\d+(?:\\.\\d+)?|inf)) LUFS");
    private static final Pattern TRUE_PEAK =
            Pattern.compile("True peak:\\s+Peak:\\s+(-?(?:\\d+(?:\\.\\d+)?|inf)) dBFS");

    // Floor for silence, where ffmpeg reports -inf: the quietest level a 24-bit file can hold.
    private static final double SILENCE_DB = -144.0;

    private final ProcessRunner runner;
    private final AudioProperties properties;

    FfmpegLoudnessMeter(ProcessRunner runner, AudioProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    @Override
    public Loudness measure(Path source) {
        Path absolute = source.toAbsolutePath();
        List<String> command = List.of(
                properties.ffmpeg(),
                "-nostdin",
                "-hide_banner",
                "-nostats",
                "-i",
                absolute.toString(),
                "-map",
                "0:a:0",
                "-vn",
                "-af",
                // framelog=verbose keeps the per-frame lines out of the default log level.
                "ebur128=peak=true:framelog=verbose",
                "-f",
                "null",
                "-");
        ProcessResult result = runner.run(command, absolute.getParent(), properties.stepTimeout());
        int summaryStart = result.stderr().lastIndexOf("Summary:");
        if (!result.succeeded() || summaryStart < 0) {
            throw new IllegalStateException("ffmpeg could not measure loudness: " + result.stderr());
        }
        String summary = result.stderr().substring(summaryStart);
        return new Loudness(valueOf(INTEGRATED, summary), valueOf(TRUE_PEAK, summary));
    }

    private static double valueOf(Pattern pattern, String summary) {
        Matcher matcher = pattern.matcher(summary);
        if (!matcher.find()) {
            throw new IllegalStateException("Unexpected ebur128 summary: " + summary);
        }
        String value = matcher.group(1);
        if (value.endsWith("inf")) {
            return SILENCE_DB;
        }
        return Math.max(SILENCE_DB, Math.round(Double.parseDouble(value) * 10) / 10.0);
    }
}
