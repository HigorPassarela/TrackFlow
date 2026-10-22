package dev.higor.trackflow.api.project.web;

import dev.higor.trackflow.api.project.application.EditProject;
import dev.higor.trackflow.api.project.domain.Project;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Body of {@code PATCH /api/projects/{projectId}}: absent fields stay as they are. */
public record UpdateProjectRequest(
        @Nullable @Size(max = Project.MAX_TITLE_LENGTH) String title,

        @Nullable @Size(max = Project.MAX_DESCRIPTION_LENGTH)
        String description,

        @Nullable @Size(max = Project.MAX_GENRE_LENGTH) String genre) {

    /** Same rule as creation: the title can change but never become empty. Reported as {@code titleValid}. */
    @AssertTrue(message = "title must not be blank")
    public boolean isTitleValid() {
        return title == null || !title.isBlank();
    }

    EditProject toCommand(UUID projectId, UUID userId) {
        return new EditProject(projectId, userId, title, description, genre);
    }
}
