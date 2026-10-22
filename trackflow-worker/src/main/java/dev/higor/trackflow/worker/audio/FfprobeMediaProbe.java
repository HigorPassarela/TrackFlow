package dev.higor.trackflow.worker.audio;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.higor.trackflow.worker.process.ProcessRunner;
import dev.higor.trackflow.worker.process.ProcessRunner.ProcessResult;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** {@link MediaProbe} backed by {@code ffprobe -print_format json}. */
@Component
class FfprobeMediaProbe implements MediaProbe {

    private final ProcessRunner runner;
    private final JsonMapper json;
    private final AudioProperties properties;

    FfprobeMediaProbe(ProcessRunner runner, JsonMapper json, AudioProperties properties) {
        this.runner = runner;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public MediaInfo probe(Path file) {
        Path absolute = file.toAbsolutePath();
        List<String> command = List.of(
                properties.ffprobe(),
                "-v",
                "error",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                absolute.toString());
        ProcessResult result = runner.run(command, absolute.getParent(), properties.stepTimeout());
        if (!result.succeeded()) {
            throw new InvalidMediaException(InvalidMediaException.UNREADABLE_FILE, "ffprobe could not read the file");
        }
        ProbeOutput output = json.readValue(result.stdout(), ProbeOutput.class);
        List<ProbeStream> streams = output.streamsOrEmpty();
        if (streams.isEmpty()) {
            // ffprobe sometimes accepts random bytes as a raw format with no streams at all.
            throw new InvalidMediaException(InvalidMediaException.UNREADABLE_FILE, "No media streams found");
        }
        boolean hasAudio = streams.stream().anyMatch(stream -> "audio".equals(stream.codecType()));
        return new MediaInfo(hasAudio, durationMs(output));
    }

    /** The container's duration, or the longest stream's when the container does not say. */
    private static long durationMs(ProbeOutput output) {
        if (output.format() != null && output.format().duration() != null) {
            return toMillis(output.format().duration());
        }
        return output.streamsOrEmpty().stream()
                .map(ProbeStream::duration)
                .filter(Objects::nonNull)
                .mapToLong(FfprobeMediaProbe::toMillis)
                .max()
                .orElse(0);
    }

    private static long toMillis(String seconds) {
        return new BigDecimal(seconds).movePointRight(3).longValue();
    }

    record ProbeOutput(
            @Nullable List<ProbeStream> streams, @Nullable ProbeFormat format) {

        List<ProbeStream> streamsOrEmpty() {
            return streams != null ? streams : List.of();
        }
    }

    record ProbeStream(
            @JsonProperty("codec_type") @Nullable String codecType,
            @Nullable String duration) {}

    record ProbeFormat(@Nullable String duration) {}
}
