package dev.higor.trackflow.worker.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The one place where the worker starts external processes (ffprobe, ffmpeg).
 *
 * <ul>
 *   <li>Arguments are a list, never a shell string: a file name like {@code "a; rm -rf ~"} stays a
 *       file name.
 *   <li>Every call has a timeout and the process is killed when it expires.
 *   <li>stdout and stderr are drained concurrently, otherwise a chatty process fills the pipe buffer
 *       and blocks forever.
 * </ul>
 */
@Component
public class ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);
    private static final int MAX_STDERR_CHARS = 4_000;

    // Reading a pipe blocks; virtual threads make that free instead of tying up the common pool.
    private static final ExecutorService STREAM_READERS = Executors.newVirtualThreadPerTaskExecutor();

    public ProcessResult run(List<String> command, Path workingDirectory, Duration timeout) {
        Process process = start(command, workingDirectory);
        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new ProcessFailedException(
                        command.getFirst() + " timed out after " + timeout, truncated(stderr.join()));
            }
            ProcessResult result = new ProcessResult(process.exitValue(), stdout.join(), truncated(stderr.join()));
            log.debug("Process finished command={} exitCode={}", command.getFirst(), result.exitCode());
            return result;
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ProcessFailedException(command.getFirst() + " was interrupted", "");
        }
    }

    private static Process start(List<String> command, Path workingDirectory) {
        try {
            return new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .start();
        } catch (IOException cannotStart) {
            throw new ProcessFailedException("Could not start " + command.getFirst(), cannotStart.getMessage());
        }
    }

    private static CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try (stream) {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException readFailure) {
                        return "";
                    }
                },
                STREAM_READERS);
    }

    private static String truncated(String text) {
        return text.length() <= MAX_STDERR_CHARS ? text : text.substring(text.length() - MAX_STDERR_CHARS);
    }

    public record ProcessResult(int exitCode, String stdout, String stderr) {

        public boolean succeeded() {
            return exitCode == 0;
        }
    }

    /** Infrastructure failure (missing binary, timeout). Bad input files are reported by callers. */
    public static class ProcessFailedException extends RuntimeException {

        private final String stderr;

        public ProcessFailedException(String message, String stderr) {
            super(message);
            this.stderr = stderr;
        }

        public String stderr() {
            return stderr;
        }
    }
}
