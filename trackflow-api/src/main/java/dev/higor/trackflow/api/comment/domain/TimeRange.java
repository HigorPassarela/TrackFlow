package dev.higor.trackflow.api.comment.domain;

import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import org.jspecify.annotations.Nullable;

/**
 * A point ({@code endMs == null}) or a range of a track, in milliseconds.
 *
 * <p>The compact constructor rejects shapes that the request validation already prevents, so a
 * failure here is a bug and throws {@link IllegalArgumentException}. Fitting inside the version
 * depends on state and is a business rule.
 */
public record TimeRange(long startMs, @Nullable Long endMs) {

    public TimeRange {
        if (startMs < 0) {
            throw new IllegalArgumentException("startMs must be zero or positive, was " + startMs);
        }
        if (endMs != null && endMs <= startMs) {
            throw new IllegalArgumentException("endMs must be after startMs");
        }
    }

    public static TimeRange point(long positionMs) {
        return new TimeRange(positionMs, null);
    }

    public void requireWithin(long durationMs) {
        long lastMs = endMs != null ? endMs : startMs;
        if (lastMs > durationMs) {
            throw new BusinessRuleException(
                    "position-beyond-duration",
                    "Position %d ms is beyond the version duration of %d ms".formatted(lastMs, durationMs));
        }
    }
}
