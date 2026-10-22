package dev.higor.trackflow.worker.audio;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code trackflow.worker.audio.*}; defaults in {@code application.yml}.
 *
 * @param maxDuration longest track the spec accepts
 * @param stepTimeout limit for each ffmpeg or ffprobe call (design D7)
 * @param ffmpeg executable, resolved through the {@code PATH} unless absolute
 * @param ffprobe executable, resolved through the {@code PATH} unless absolute
 */
@ConfigurationProperties("trackflow.worker.audio")
public record AudioProperties(Duration maxDuration, Duration stepTimeout, String ffmpeg, String ffprobe) {}
