package dev.higor.trackflow.api.ableton.web;

import dev.higor.trackflow.api.ableton.domain.AbletonDiff;
import java.util.UUID;

/**
 * The diff as computed by the domain, plus which versions it compares. {@link AbletonDiff} is an
 * immutable value without persistence, so it is exposed as is instead of being copied field by
 * field.
 *
 * @param fromVersionId the version given in {@code against}
 * @param toVersionId the version in the path
 */
public record DiffResponse(UUID fromVersionId, UUID toVersionId, AbletonDiff changes) {

    static DiffResponse from(UUID fromVersionId, UUID toVersionId, AbletonDiff changes) {
        return new DiffResponse(fromVersionId, toVersionId, changes);
    }
}
