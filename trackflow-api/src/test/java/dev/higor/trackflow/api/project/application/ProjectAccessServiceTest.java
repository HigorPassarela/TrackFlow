package dev.higor.trackflow.api.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.domain.ProjectFixtures;
import dev.higor.trackflow.api.project.domain.ProjectMemberRepository;
import dev.higor.trackflow.api.project.domain.ProjectRepository;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private ProjectMemberRepository members;

    @Mock
    private ProjectRepository projects;

    private ProjectAccessService access;

    @BeforeEach
    void setUp() {
        access = new ProjectAccessService(members, projects);
    }

    @ParameterizedTest(name = "{0} doing what needs {1}: allowed={2}")
    @CsvSource({
        "LISTENER, LISTENER, true",
        "LISTENER, EDITOR, false",
        "LISTENER, OWNER, false",
        "EDITOR, LISTENER, true",
        "EDITOR, EDITOR, true",
        "EDITOR, OWNER, false",
        "OWNER, LISTENER, true",
        "OWNER, EDITOR, true",
        "OWNER, OWNER, true"
    })
    void allowsOnlyRolesAtOrAboveTheMinimum(ProjectRole role, ProjectRole minimum, boolean allowed) {
        givenMember(role);

        if (allowed) {
            assertThat(access.requireRole(PROJECT, USER, minimum)).isEqualTo(role);
        } else {
            assertThatThrownBy(() -> access.requireRole(PROJECT, USER, minimum))
                    .isInstanceOfSatisfying(
                            PermissionDeniedException.class,
                            error -> assertThat(error.code()).isEqualTo("insufficient-role"));
        }
    }

    @Test
    void treatsNonMemberAsNotFound() {
        given(members.findMembership(PROJECT, USER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireRole(PROJECT, USER, ProjectRole.LISTENER))
                .isInstanceOfSatisfying(
                        NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("project-not-found"));
    }

    @Test
    void treatsNonMemberOfArchivedProjectAsNotFoundToo() {
        given(members.findMembership(PROJECT, USER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireRoleOnActiveProject(PROJECT, USER, ProjectRole.LISTENER))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsWritesOnArchivedProject() {
        givenMember(ProjectRole.OWNER);
        given(projects.findById(PROJECT)).willReturn(Optional.of(ProjectFixtures.archivedProject()));

        assertThatThrownBy(() -> access.requireRoleOnActiveProject(PROJECT, USER, ProjectRole.EDITOR))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("project-archived"));
    }

    @Test
    void checksRoleBeforeArchiveState() {
        givenMember(ProjectRole.LISTENER);

        assertThatThrownBy(() -> access.requireRoleOnActiveProject(PROJECT, USER, ProjectRole.EDITOR))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void allowsWritesOnActiveProject() {
        givenMember(ProjectRole.EDITOR);
        given(projects.findById(PROJECT)).willReturn(Optional.of(ProjectFixtures.activeProject()));

        assertThat(access.requireRoleOnActiveProject(PROJECT, USER, ProjectRole.EDITOR))
                .isEqualTo(ProjectRole.EDITOR);
    }

    private void givenMember(ProjectRole role) {
        given(members.findMembership(PROJECT, USER))
                .willReturn(Optional.of(ProjectFixtures.member(PROJECT, USER, role)));
    }
}
