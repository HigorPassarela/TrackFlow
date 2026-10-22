package dev.higor.trackflow.api.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProjectTest {

    private static final Instant LATER = Instant.parse("2026-10-22T10:00:00Z");

    @Test
    void archivesActiveProject() {
        Project project = ProjectFixtures.activeProject();

        project.archive(LATER);

        assertThat(project.isArchived()).isTrue();
        assertThat(project.getArchivedAt()).isEqualTo(LATER);
    }

    @Test
    void rejectsArchivingTwice() {
        Project project = ProjectFixtures.archivedProject();

        assertThatThrownBy(() -> project.archive(LATER))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("project-archived"));
    }

    @Test
    void unarchivesArchivedProject() {
        Project project = ProjectFixtures.archivedProject();

        project.unarchive(LATER);

        assertThat(project.isArchived()).isFalse();
        assertThat(project.getArchivedAt()).isNull();
    }

    @Test
    void rejectsUnarchivingActiveProject() {
        Project project = ProjectFixtures.activeProject();

        assertThatThrownBy(() -> project.unarchive(LATER))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("project-not-archived"));
    }

    @Test
    void rejectsEditingArchivedProject() {
        Project project = ProjectFixtures.archivedProject();

        assertThatThrownBy(() -> project.edit("Novo título", null, null, LATER))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void editingCountsAsActivity() {
        Project project = ProjectFixtures.activeProject();

        project.edit("Ibiza Vibes (VIP)", "kick novo", " ", LATER);

        assertThat(project.getTitle()).isEqualTo("Ibiza Vibes (VIP)");
        assertThat(project.getDescription()).isEqualTo("kick novo");
        assertThat(project.getGenre()).isNull();
        assertThat(project.getLastActivityAt()).isEqualTo(LATER);
    }

    @Test
    void startsWithCreationAsLastActivity() {
        Project project = ProjectFixtures.activeProject();

        assertThat(project.getLastActivityAt()).isEqualTo(ProjectFixtures.CREATED_AT);
        assertThat(project.isArchived()).isFalse();
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> Project.builder()
                        .title("  ")
                        .ownerId(ProjectFixtures.OWNER)
                        .createdAt(LATER)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
