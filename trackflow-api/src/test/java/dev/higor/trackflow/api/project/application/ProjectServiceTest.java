package dev.higor.trackflow.api.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.domain.Project;
import dev.higor.trackflow.api.project.domain.ProjectFixtures;
import dev.higor.trackflow.api.project.domain.ProjectMember;
import dev.higor.trackflow.api.project.domain.ProjectMemberRepository;
import dev.higor.trackflow.api.project.domain.ProjectRepository;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Uses the real {@link ProjectAccessService} over mocked repositories, so each spec scenario runs
 * through the same permission checks as production.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = ProjectFixtures.OWNER;
    private static final UUID EDITOR = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID LISTENER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000f");

    @Mock
    private ProjectRepository projects;

    @Mock
    private ProjectMemberRepository members;

    @Mock
    private UserDirectory users;

    private ProjectService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectService(
                projects,
                members,
                new ProjectAccessService(members, projects),
                users,
                Clock.fixed(NOW, ZoneOffset.UTC));
        project = ProjectFixtures.activeProject();
        ReflectionTestUtils.setField(project, "id", PROJECT);
        given(projects.findById(PROJECT)).willReturn(Optional.of(project));
        given(users.displayNames(anySet())).willReturn(Map.of(OWNER, "Produtora A"));
        givenMember(OWNER, ProjectRole.OWNER);
        givenMember(EDITOR, ProjectRole.EDITOR);
        givenMember(LISTENER, ProjectRole.LISTENER);
        given(members.findMembership(PROJECT, STRANGER)).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("Criação válida: cria o projeto e registra quem criou como dono")
    void createsProjectWithCreatorAsOwner() {
        given(projects.save(any(Project.class))).willAnswer(invocation -> {
            Project saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", PROJECT);
            return saved;
        });

        ProjectView view = service.create(new CreateProject(OWNER, "Ibiza Vibes", null, "tech house"));

        assertThat(view.role()).isEqualTo(ProjectRole.OWNER);
        assertThat(view.genre()).isEqualTo("tech house");
        then(members)
                .should()
                .save(argThat((ProjectMember member) -> member.userId().equals(OWNER) && member.isOwner()));
    }

    @Test
    @DisplayName("Membro consulta projeto: ouvinte vê detalhes, nome do dono e o próprio papel")
    void listenerSeesProjectDetails() {
        ProjectView view = service.get(PROJECT, LISTENER);

        assertThat(view.title()).isEqualTo("Ibiza Vibes");
        assertThat(view.ownerName()).isEqualTo("Produtora A");
        assertThat(view.role()).isEqualTo(ProjectRole.LISTENER);
    }

    @Test
    @DisplayName("Não membro consulta projeto: recurso inexistente")
    void nonMemberGetsNotFound() {
        assertThatThrownBy(() -> service.get(PROJECT, STRANGER)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Editor altera descrição")
    void editorChangesDescription() {
        ProjectView view = service.edit(new EditProject(PROJECT, EDITOR, null, "kick novo", null));

        assertThat(view.description()).isEqualTo("kick novo");
        assertThat(view.title()).isEqualTo("Ibiza Vibes");
        assertThat(project.getLastActivityAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Ouvinte tenta editar: erro de permissão")
    void listenerCannotEdit() {
        assertThatThrownBy(() -> service.edit(new EditProject(PROJECT, LISTENER, "Outro título", null, null)))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void clearsGenreWithEmptyValue() {
        ProjectView view = service.edit(new EditProject(PROJECT, OWNER, null, null, ""));

        assertThat(view.genre()).isNull();
    }

    @Test
    @DisplayName("Dono arquiva: continua consultável e recusa alterações")
    void ownerArchivesAndProjectBecomesReadOnly() {
        service.archive(PROJECT, OWNER);

        assertThat(service.get(PROJECT, LISTENER).archivedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.edit(new EditProject(PROJECT, EDITOR, null, "x", null)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("project-archived"));
    }

    @Test
    @DisplayName("Editor tenta arquivar: erro de permissão")
    void editorCannotArchive() {
        assertThatThrownBy(() -> service.archive(PROJECT, EDITOR)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void ownerUnarchives() {
        project.archive(NOW.minusSeconds(60));

        ProjectView view = service.unarchive(PROJECT, OWNER);

        assertThat(view.archivedAt()).isNull();
    }

    private void givenMember(UUID userId, ProjectRole role) {
        given(members.findMembership(PROJECT, userId))
                .willReturn(Optional.of(ProjectFixtures.member(PROJECT, userId, role)));
    }
}
