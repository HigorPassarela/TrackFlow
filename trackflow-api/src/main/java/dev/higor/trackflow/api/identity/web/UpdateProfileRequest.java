package dev.higor.trackflow.api.identity.web;

import dev.higor.trackflow.api.identity.domain.User;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /api/me}. The artistic name is the only editable field. */
public record UpdateProfileRequest(
        @NotNull
        @Size(min = User.MIN_DISPLAY_NAME_LENGTH, max = User.MAX_DISPLAY_NAME_LENGTH)
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "must not start or end with spaces")
        String displayName) {}
