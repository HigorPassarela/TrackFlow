package dev.higor.trackflow.api.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

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
import java.util.LinkedHashMap;
import java.util.List;
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

/** Memberships live in a map behind the mocked repository, so removals are visible to later calls. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberServiceTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = ProjectFixtures.OWNER;
    private static final UUID EDITOR = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID LISTENER = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Mock
    private ProjectMemberRepository members;

    @Mock
    private ProjectRepository projects;

    @Mock
    private UserDirectory users;

    private final Map<UUID, ProjectMember> memberships = new LinkedHashMap<>();
    private final Project project = ProjectFixtures.activeProject();
    private MemberService service;

    @BeforeEach
    void setUp() {
        service = new MemberService(members, new ProjectAccessService(members, projects), users);
        memberships.put(OWNER, ProjectFixtures.member(PROJECT, OWNER, ProjectRole.OWNER));
        memberships.put(EDITOR, ProjectFixtures.member(PROJECT, EDITOR, ProjectRole.EDITOR));
        memberships.put(LISTENER, ProjectFixtures.member(PROJECT, LISTENER, ProjectRole.LISTENER));
        given(members.findMembership(eq(PROJECT), any()))
                .willAnswer(invocation -> Optional.ofNullable(memberships.get(invocation.<UUID>getArgument(1))));
        given(members.findAllByIdProjectId(PROJECT)).willAnswer(invocation -> List.copyOf(memberships.values()));
        willAnswer(invocation -> memberships.remove(
                        invocation.<ProjectMember>getArgument(0).userId()))
                .given(members)
                .delete(any(ProjectMember.class));
        given(projects.findById(PROJECT)).willReturn(Optional.of(project));
        given(users.displayNames(anySet())).willReturn(Map.of(OWNER, "Produtora A", LISTENER, "Ouvinte"));
    }

    @Test
    void anyMemberListsMembersOwnerFirst() {
        var list = service.list(PROJECT, LISTENER);

        assertThat(list)
                .extracting(MemberView::role)
                .containsExactly(ProjectRole.OWNER, ProjectRole.EDITOR, ProjectRole.LISTENER);
        assertThat(list.getFirst().displayName()).isEqualTo("Produtora A");
    }

    @Test
    @DisplayName("Promover ouvinte: passa a ter permissões de editor na próxima requisição")
    void promotedListenerGetsEditorPermissionsOnNextRequest() {
        service.changeRole(PROJECT, LISTENER, ProjectRole.EDITOR, OWNER);

        assertThat(new ProjectAccessService(members, projects).requireRole(PROJECT, LISTENER, ProjectRole.EDITOR))
                .isEqualTo(ProjectRole.EDITOR);
    }

    @Test
    void onlyTheOwnerChangesRoles() {
        assertThatThrownBy(() -> service.changeRole(PROJECT, LISTENER, ProjectRole.EDITOR, EDITOR))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void ownerCannotDemoteThemselves() {
        assertThatThrownBy(() -> service.changeRole(PROJECT, OWNER, ProjectRole.EDITOR, OWNER))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("owner-role-cannot-change"));
    }

    @Test
    @DisplayName("Remover membro: o ex-membro passa a receber o projeto como inexistente")
    void removedMemberSeesProjectAsNotFound() {
        service.remove(PROJECT, EDITOR, OWNER);

        assertThatThrownBy(() -> service.list(PROJECT, EDITOR)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Dono tenta se remover: o projeto precisa de um dono")
    void ownerCannotRemoveThemselves() {
        assertThatThrownBy(() -> service.remove(PROJECT, OWNER, OWNER))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("owner-cannot-leave"));
        assertThat(memberships).containsKey(OWNER);
    }

    @Test
    void removingSomeoneWhoIsNotAMemberIsNotFound() {
        UUID stranger = UUID.fromString("00000000-0000-0000-0000-00000000000f");

        assertThatThrownBy(() -> service.remove(PROJECT, stranger, OWNER))
                .isInstanceOfSatisfying(
                        NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("member-not-found"));
    }

    @Test
    void editorCannotRemoveMembers() {
        assertThatThrownBy(() -> service.remove(PROJECT, LISTENER, EDITOR))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void membershipChangesAreRefusedOnArchivedProject() {
        project.archive(ProjectFixtures.CREATED_AT.plusSeconds(60));

        assertThatThrownBy(() -> service.remove(PROJECT, LISTENER, OWNER))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("project-archived"));
    }

    @Test
    @DisplayName("Ouvinte sai do projeto")
    void listenerLeaves() {
        service.leave(PROJECT, LISTENER);

        assertThat(memberships).doesNotContainKey(LISTENER);
    }

    @Test
    void ownerCannotLeave() {
        assertThatThrownBy(() -> service.leave(PROJECT, OWNER)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void leavingIsAllowedOnArchivedProject() {
        project.archive(ProjectFixtures.CREATED_AT.plusSeconds(60));

        service.leave(PROJECT, EDITOR);

        assertThat(memberships).doesNotContainKey(EDITOR);
    }
}
