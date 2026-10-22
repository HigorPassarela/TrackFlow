package dev.higor.trackflow.api.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.domain.Project;
import dev.higor.trackflow.api.project.domain.ProjectFixtures;
import dev.higor.trackflow.api.project.domain.ProjectInvite;
import dev.higor.trackflow.api.project.domain.ProjectInviteRepository;
import dev.higor.trackflow.api.project.domain.ProjectMember;
import dev.higor.trackflow.api.project.domain.ProjectMemberRepository;
import dev.higor.trackflow.api.project.domain.ProjectRepository;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
import dev.higor.trackflow.api.shared.security.TokenGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InviteServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID INVITE = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID OWNER = ProjectFixtures.OWNER;
    private static final UUID EDITOR = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @Mock
    private ProjectInviteRepository invites;

    @Mock
    private ProjectMemberRepository members;

    @Mock
    private ProjectRepository projects;

    @Mock
    private UserDirectory users;

    private final TokenGenerator tokens = new TokenGenerator();
    private final Map<UUID, ProjectMember> memberships = new HashMap<>();
    private final Project project = ProjectFixtures.activeProject();
    private InviteService service;

    @BeforeEach
    void setUp() {
        service = invitesAt(NOW);
        memberships.put(OWNER, ProjectFixtures.member(PROJECT, OWNER, ProjectRole.OWNER));
        memberships.put(EDITOR, ProjectFixtures.member(PROJECT, EDITOR, ProjectRole.EDITOR));
        given(members.findMembership(eq(PROJECT), any()))
                .willAnswer(invocation -> Optional.ofNullable(memberships.get(invocation.<UUID>getArgument(1))));
        given(projects.findById(PROJECT)).willReturn(Optional.of(project));
        given(users.displayNames(anySet())).willReturn(Map.of(OWNER, "Produtora A"));
        given(invites.save(any(ProjectInvite.class))).willAnswer(invocation -> {
            ProjectInvite saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", INVITE);
            return saved;
        });
    }

    @Test
    @DisplayName("Dono gera convite: recebe o link e a expiração; o banco guarda só o hash")
    void storesOnlyTheHashAndReturnsTheTokenOnce() {
        CreatedInvite created = service.create(PROJECT, ProjectRole.EDITOR, OWNER);

        var saved = ArgumentCaptor.forClass(ProjectInvite.class);
        then(invites).should().save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(tokens.hash(created.token()))
                .isNotEqualTo(created.token());
        assertThat(created.invite().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(created.invite().role()).isEqualTo(ProjectRole.EDITOR);
    }

    @Test
    @DisplayName("Editor tenta gerar convite: erro de permissão")
    void editorCannotCreateInvites() {
        assertThatThrownBy(() -> service.create(PROJECT, ProjectRole.LISTENER, EDITOR))
                .isInstanceOf(PermissionDeniedException.class);
        then(invites).should(never()).save(any());
    }

    @Test
    void archivedProjectAcceptsNoNewInvites() {
        project.archive(NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.create(PROJECT, ProjectRole.LISTENER, OWNER))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("project-archived"));
    }

    @Test
    @DisplayName("Dono lista convites: só os pendentes, sem o link")
    void listsPendingInvitesOfTheProject() {
        ProjectInvite pending = existingInvite("pending-token", NOW.minus(Duration.ofDays(1)));
        given(invites.findPending(PROJECT, NOW)).willReturn(List.of(pending));

        var list = service.listPending(PROJECT, OWNER);

        assertThat(list).singleElement().satisfies(view -> {
            assertThat(view.createdByName()).isEqualTo("Produtora A");
            assertThat(view.role()).isEqualTo(ProjectRole.LISTENER);
        });
    }

    @Test
    void revokesPendingInvite() {
        ProjectInvite invite = existingInvite("revoke-me", NOW.minusSeconds(60));
        given(invites.findByIdAndProjectId(INVITE, PROJECT)).willReturn(Optional.of(invite));

        service.revoke(PROJECT, INVITE, OWNER);

        assertThat(invite.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Convite válido: vira ouvinte e o convite não pode mais ser usado")
    void acceptingAddsTheUserWithTheInviteRole() {
        ProjectInvite invite = existingInvite("valid-token", NOW.minusSeconds(60));

        AcceptedInvite accepted = service.accept("valid-token", GUEST);

        assertThat(accepted.role()).isEqualTo(ProjectRole.LISTENER);
        then(members)
                .should()
                .save(argThat((ProjectMember member) ->
                        member.userId().equals(GUEST) && member.getRole() == ProjectRole.LISTENER));
        assertThat(invite.isPending(NOW)).isFalse();
    }

    @Test
    @DisplayName("Convite expirado")
    void refusesExpiredInvite() {
        existingInvite("old-token", NOW.minus(Duration.ofDays(8)));

        assertThatThrownBy(() -> service.accept("old-token", GUEST))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("invite-expired"));
        then(members).should(never()).save(any());
    }

    @Test
    @DisplayName("Convite já usado: um segundo usuário é recusado")
    void refusesInviteAlreadyUsed() {
        existingInvite("used-token", NOW.minusSeconds(60));
        service.accept("used-token", GUEST);
        UUID secondGuest = UUID.fromString("00000000-0000-0000-0000-00000000000e");

        assertThatThrownBy(() -> service.accept("used-token", secondGuest))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("invite-not-available"));
    }

    @Test
    @DisplayName("Convite revogado")
    void refusesRevokedInvite() {
        ProjectInvite invite = existingInvite("revoked-token", NOW.minusSeconds(60));
        invite.revoke(NOW.minusSeconds(30));

        assertThatThrownBy(() -> service.accept("revoked-token", GUEST))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("invite-not-available"));
    }

    @Test
    @DisplayName("Usuário já é membro: recusa sem mudar o papel nem consumir o convite")
    void refusesExistingMemberWithoutConsumingTheInvite() {
        ProjectInvite invite = existingInvite("member-token", NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.accept("member-token", EDITOR))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("already-member"));
        assertThat(invite.isPending(NOW)).isTrue();
        assertThat(memberships.get(EDITOR).getRole()).isEqualTo(ProjectRole.EDITOR);
        then(members).should(never()).save(any());
    }

    @Test
    void unknownTokenIsNotFound() {
        given(invites.findByTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept("nope", GUEST))
                .isInstanceOfSatisfying(
                        NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("invite-not-found"));
    }

    private ProjectInvite existingInvite(String token, Instant createdAt) {
        ProjectInvite invite = ProjectInvite.builder()
                .projectId(PROJECT)
                .tokenHash(tokens.hash(token))
                .role(ProjectRole.LISTENER)
                .createdBy(OWNER)
                .createdAt(createdAt)
                .build();
        ReflectionTestUtils.setField(invite, "id", INVITE);
        given(invites.findByTokenHash(tokens.hash(token))).willReturn(Optional.of(invite));
        return invite;
    }

    private InviteService invitesAt(Instant now) {
        return new InviteService(
                invites,
                members,
                projects,
                new ProjectAccessService(members, projects),
                users,
                tokens,
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
