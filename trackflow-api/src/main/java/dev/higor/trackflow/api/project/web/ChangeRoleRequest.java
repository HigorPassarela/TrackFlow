package dev.higor.trackflow.api.project.web;

import dev.higor.trackflow.api.project.ProjectRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

/** Only editor and listener can be assigned: ownership is not transferable in the MVP. */
public record ChangeRoleRequest(
        @NotNull @Pattern(regexp = "editor|listener", message = "must be editor or listener")
        String role) {

    ProjectRole toRole() {
        return ProjectRole.valueOf(role.toUpperCase(Locale.ROOT));
    }
}
