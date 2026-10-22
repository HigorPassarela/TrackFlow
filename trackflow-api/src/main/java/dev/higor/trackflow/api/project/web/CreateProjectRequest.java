package dev.higor.trackflow.api.project.web;

import dev.higor.trackflow.api.project.application.CreateProject;
import dev.higor.trackflow.api.project.domain.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CreateProjectRequest(
        @NotBlank @Size(max = Project.MAX_TITLE_LENGTH) String title,

        @Nullable @Size(max = Project.MAX_DESCRIPTION_LENGTH)
        String description,

        @Nullable @Size(max = Project.MAX_GENRE_LENGTH) String genre) {

    CreateProject toCommand(UUID ownerId) {
        return new CreateProject(ownerId, title, description, genre);
    }
}
