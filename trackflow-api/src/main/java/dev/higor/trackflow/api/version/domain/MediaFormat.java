package dev.higor.trackflow.api.version.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Formats accepted for upload. Recognized by the file extension; the declared content type must be
 * one the browsers actually send for it. The real content is only checked later, by the worker.
 */
public enum MediaFormat {
    WAV(Set.of("wav"), Set.of("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave")),
    AIFF(Set.of("aif", "aiff"), Set.of("audio/aiff", "audio/x-aiff")),
    FLAC(Set.of("flac"), Set.of("audio/flac", "audio/x-flac")),
    MP3(Set.of("mp3"), Set.of("audio/mpeg", "audio/mp3")),
    MP4(Set.of("mp4"), Set.of("video/mp4", "audio/mp4")),
    MOV(Set.of("mov"), Set.of("video/quicktime")),
    MKV(Set.of("mkv"), Set.of("video/x-matroska", "video/matroska")),
    WEBM(Set.of("webm"), Set.of("video/webm", "audio/webm"));

    /** Browsers fall back to this for types they do not know (often FLAC and MKV). */
    private static final String GENERIC_BINARY = "application/octet-stream";

    private final Set<String> extensions;
    private final Set<String> contentTypes;

    MediaFormat(Set<String> extensions, Set<String> contentTypes) {
        this.extensions = extensions;
        this.contentTypes = contentTypes;
    }

    public static Optional<MediaFormat> ofFileName(String fileName) {
        String extension = extensionOf(fileName);
        return Arrays.stream(values())
                .filter(format -> format.extensions.contains(extension))
                .findFirst();
    }

    /** {@code "WAV, AIFF, FLAC, MP3, MP4, MOV, MKV, WEBM"}, for error messages. */
    public static String acceptedList() {
        return String.join(", ", Arrays.stream(values()).map(Enum::name).toList());
    }

    public boolean accepts(String contentType) {
        String normalized =
                contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
        return contentTypes.contains(normalized) || normalized.equals(GENERIC_BINARY);
    }

    /** Extension of the stored object, the same for every alias of the format. */
    public String storageExtension() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
