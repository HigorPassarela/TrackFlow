package dev.higor.trackflow.worker.audio;

import dev.higor.trackflow.contracts.ProcessAudioJob;
import dev.higor.trackflow.worker.audio.LoudnessMeter.Loudness;
import dev.higor.trackflow.worker.audio.PcmAnalyzer.PcmAnalysis;
import dev.higor.trackflow.worker.storage.ObjectStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * The audio pipeline of design D7, for one job: download, probe, decode and analyze, encode the
 * preview, measure loudness, upload the outputs.
 *
 * <p>{@link InvalidMediaException} means the file itself is bad; any other exception is treated as
 * temporary by the caller and retried. Outputs use deterministic keys next to the source, so a
 * retry overwrites what a failed attempt left behind.
 */
@Service
public class AudioJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(AudioJobProcessor.class);

    static final String PREVIEW_FILE = "preview.mp3";
    static final String PEAKS_FILE = "peaks.json";

    private final ObjectStorage storage;
    private final MediaProbe probe;
    private final Transcoder transcoder;
    private final LoudnessMeter loudnessMeter;
    private final JsonMapper json;
    private final AudioProperties properties;

    public AudioJobProcessor(
            ObjectStorage storage,
            MediaProbe probe,
            Transcoder transcoder,
            LoudnessMeter loudnessMeter,
            JsonMapper json,
            AudioProperties properties) {
        this.storage = storage;
        this.probe = probe;
        this.transcoder = transcoder;
        this.loudnessMeter = loudnessMeter;
        this.json = json;
        this.properties = properties;
    }

    public ProcessedAudio process(ProcessAudioJob job) {
        Path workDirectory = createWorkDirectory(job);
        try {
            Path source = workDirectory.resolve("source" + extensionOf(job.sourceKey()));
            storage.download(job.sourceKey(), source);
            probe.probe(source).requireProcessable(properties.maxDuration());

            Path pcm = workDirectory.resolve("audio.pcm");
            transcoder.decodeToPcm(source, pcm);
            PcmAnalysis analysis = PcmAnalyzer.analyze(pcm, Transcoder.PCM_SAMPLE_RATE);
            // The container may not state a duration (screen recordings often don't); the decoded stream always does.
            if (analysis.durationMs() > properties.maxDuration().toMillis()) {
                throw new InvalidMediaException(
                        InvalidMediaException.DURATION_EXCEEDED, "Decoded audio is longer than the limit");
            }

            Path preview = workDirectory.resolve(PREVIEW_FILE);
            transcoder.encodePreview(source, preview);
            Loudness loudness = loudnessMeter.measure(source);
            Path peaks = workDirectory.resolve(PEAKS_FILE);
            json.writeValue(peaks.toFile(), new PeaksDocument(PeaksCalculator.POINTS_PER_SECOND, analysis.peaks()));

            String prefix = job.sourceKey().substring(0, job.sourceKey().lastIndexOf('/') + 1);
            storage.upload(preview, prefix + PREVIEW_FILE, "audio/mpeg");
            storage.upload(peaks, prefix + PEAKS_FILE, "application/json");
            log.info(
                    "Audio processed versionId={} jobId={} durationMs={} bpm={}",
                    job.versionId(),
                    job.jobId(),
                    analysis.durationMs(),
                    analysis.bpm());
            return new ProcessedAudio(
                    prefix + PREVIEW_FILE,
                    prefix + PEAKS_FILE,
                    analysis.durationMs(),
                    analysis.bpm(),
                    loudness.integratedLufs(),
                    loudness.truePeakDbtp());
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static Path createWorkDirectory(ProcessAudioJob job) {
        try {
            return Files.createTempDirectory("trackflow-" + job.jobId());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a work directory", e);
        }
    }

    /** Keeps the extension so ffprobe gets a hint; the content still decides (spec: validation by content). */
    private static String extensionOf(String key) {
        int slash = key.lastIndexOf('/');
        int dot = key.lastIndexOf('.');
        return dot > slash ? key.substring(dot) : "";
    }

    private static void deleteRecursively(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("Could not delete work directory path={}", directory, e);
        }
    }

    /** What the player downloads as {@code peaks.json}. */
    record PeaksDocument(int pointsPerSecond, List<Double> peaks) {}

    /** Outputs of a successful run, ready for {@code AudioProcessed.Succeeded}. */
    public record ProcessedAudio(
            String previewKey,
            String peaksKey,
            long durationMs,
            @Nullable Double bpmEstimated,
            double loudnessLufs,
            double truePeakDbtp) {}
}
