package dev.higor.trackflow.api.project.application;

import static java.util.stream.Collectors.toSet;

import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.domain.ProjectInvite;
import dev.higor.trackflow.api.project.domain.ProjectInviteRepository;
import dev.higor.trackflow.api.project.domain.ProjectMember;
import dev.higor.trackflow.api.project.domain.ProjectMemberRepository;
import dev.higor.trackflow.api.project.domain.ProjectRepository;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.security.TokenGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Invite links: only the owner creates, lists and revokes them; any signed-in user may accept one. */
@Service
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);

    private final ProjectInviteRepository invites;
    private final ProjectMemberRepository members;
    private final ProjectRepository projects;
    private final ProjectAccess access;
    private final UserDirectory users;
    private final TokenGenerator tokens;
    private final Clock clock;

    public InviteService(
            ProjectInviteRepository invites,
            ProjectMemberRepository members,
            ProjectRepository projects,
            ProjectAccess access,
            UserDirectory users,
            TokenGenerator tokens,
            Clock clock) {
        this.invites = invites;
        this.members = members;
        this.projects = projects;
        this.access = access;
        this.users = users;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public CreatedInvite create(UUID projectId, ProjectRole role, UUID userId) {
        access.requireRoleOnActiveProject(projectId, userId, ProjectRole.OWNER);
        TokenGenerator.GeneratedToken token = tokens.generate();
        ProjectInvite invite = invites.save(ProjectInvite.builder()
                .projectId(projectId)
                .tokenHash(token.hash())
                .role(role)
                .createdBy(userId)
                .createdAt(clock.instant())
                .build());
        log.info("Invite created inviteId={} projectId={} role={}", invite.getId(), projectId, role);
        return new CreatedInvite(InviteView.of(invite, names(Set.of(userId))), token.value());
    }

    @Transactional(readOnly = true)
    public List<InviteView> listPending(UUID projectId, UUID userId) {
        access.requireRole(projectId, userId, ProjectRole.OWNER);
        List<ProjectInvite> pending = invites.findPending(projectId, clock.instant());
        Map<UUID, String> names =
                names(pending.stream().map(ProjectInvite::getCreatedBy).collect(toSet()));
        return pending.stream().map(invite -> InviteView.of(invite, names)).toList();
    }

    @Transactional
    public void revoke(UUID projectId, UUID inviteId, UUID userId) {
        access.requireRole(projectId, userId, ProjectRole.OWNER);
        ProjectInvite invite = invites.findByIdAndProjectId(inviteId, projectId)
                .orElseThrow(() -> NotFoundException.of("invite", inviteId));
        invite.revoke(clock.instant());
        log.info("Invite revoked inviteId={} projectId={}", inviteId, projectId);
    }

    /**
     * Membership is checked before the invite is consumed: a member who opens a link of their own
     * project keeps their role and leaves the link usable for the intended person.
     */
    @Transactional
    public AcceptedInvite accept(String token, UUID userId) {
        String hash = tokens.hash(token);
        ProjectInvite invite = invites.findByTokenHash(hash)
                .filter(found -> tokens.matches(token, found.getTokenHash()))
                .orElseThrow(() -> new NotFoundException("invite-not-found", "Invite not found"));
        UUID projectId = invite.getProjectId();
        if (members.findMembership(projectId, userId).isPresent()) {
            throw new BusinessRuleException("already-member", "You are already a member of this project");
        }
        projects.findById(projectId)
                .orElseThrow(() -> NotFoundException.of("project", projectId))
                .requireActive();

        Instant now = clock.instant();
        invite.accept(userId, now);
        members.save(ProjectMember.builder()
                .projectId(projectId)
                .userId(userId)
                .role(invite.getRole())
                .joinedAt(now)
                .build());
        log.info("Invite accepted inviteId={} projectId={} userId={}", invite.getId(), projectId, userId);
        return new AcceptedInvite(projectId, invite.getRole());
    }

    private Map<UUID, String> names(Set<UUID> userIds) {
        return users.displayNames(userIds);
    }
}
