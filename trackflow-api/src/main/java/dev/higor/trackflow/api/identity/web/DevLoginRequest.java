package dev.higor.trackflow.api.identity.web;

import dev.higor.trackflow.api.identity.domain.User;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DevLoginRequest(
        @NotNull
        @Size(min = User.MIN_DISPLAY_NAME_LENGTH, max = User.MAX_DISPLAY_NAME_LENGTH)
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$", message = "use lowercase letters, digits and hyphens")
        String name) {}
