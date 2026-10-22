package dev.higor.trackflow.api.version;

import org.jspecify.annotations.Nullable;

/** Everything the worker produced for a successfully processed version. */
public record ReadyMedia(
        String previewKey,
        String peaksKey,
        long durationMs,
        @Nullable Double bpmEstimated,
        double loudnessLufs,
        double truePeakDbtp) {}
