package dev.higor.trackflow.worker.ableton;

import dev.higor.trackflow.contracts.AnalyzeAbletonProjectJob;
import dev.higor.trackflow.worker.storage.ObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Downloads the project of one job and reads it. {@link InvalidProjectException} means the file
 * itself is bad; anything else is treated as temporary by the caller and retried.
 */
@Service
public class AbletonJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(AbletonJobProcessor.class);

    private final ObjectStorage storage;
    private final AbletonProjectReader reader;

    public AbletonJobProcessor(ObjectStorage storage, AbletonProjectReader reader) {
        this.storage = storage;
        this.reader = reader;
    }

    public AbletonProject process(AnalyzeAbletonProjectJob job) {
        Path file = createTempFile(job);
        try {
            storage.download(job.alsKey(), file);
            try (InputStream als = Files.newInputStream(file)) {
                AbletonProject project = reader.read(als);
                log.info(
                        "Ableton project read versionId={} jobId={} tracks={}",
                        job.versionId(),
                        job.jobId(),
                        project.tracks().size());
                return project;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read downloaded project for versionId=" + job.versionId(), e);
        } finally {
            deleteQuietly(file);
        }
    }

    private static Path createTempFile(AnalyzeAbletonProjectJob job) {
        try {
            return Files.createTempFile("trackflow-" + job.jobId(), ".als");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a work file", e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete work file path={}", file, e);
        }
    }
}
