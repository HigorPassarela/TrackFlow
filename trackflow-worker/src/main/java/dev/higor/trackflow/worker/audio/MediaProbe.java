package dev.higor.trackflow.worker.audio;

import java.nio.file.Path;

/** Looks inside a file by its content, never by its extension or declared type. */
public interface MediaProbe {

    /** @throws InvalidMediaException with {@code unreadable-file} when the content is not media */
    MediaInfo probe(Path file);
}
