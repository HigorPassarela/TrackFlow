package dev.higor.trackflow.api.project.application;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toSet;

import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.domain.ProjectMember;
import dev.higor.trackflow.api.project.domain.ProjectMemberRepository;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who is in a project and with which role. Roles are read from the database on every request, so
 * a promotion or removal takes effect on the member's next call.
 */
@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    /** Owner first, then editors, then listeners; oldest members first inside each role. */
    private static final Comparator<ProjectMember> BY_ROLE_THEN_SENIORITY =
            comparing(ProjectMember::getRole, Comparator.reverseOrder()).thenComparing(ProjectMember::getJoinedAt);

    private final ProjectMemberRepository members;
    private final ProjectAccess access;
    private final UserDirectory users;

    public MemberService(ProjectMemberRepository members, ProjectAccess access, UserDirectory users) {
        this.members = members;
        this.access = access;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<MemberView> list(UUID projectId, UUID userId) {
        access.requireRole(projectId, userId, ProjectRole.LISTENER);
        List<ProjectMember> all = members.findAllByIdProjectId(projectId);
        Map<UUID, String> names =
                users.displayNames(all.stream().map(ProjectMember::userId).collect(toSet()));
        return all.stream()
                .sorted(BY_ROLE_THEN_SENIORITY)
                .map(member -> MemberView.of(member, names))
                .toList();
    }

    @Transactional
    public MemberView changeRole(UUID projectId, UUID memberId, ProjectRole newRole, UUID userId) {
        access.requireRoleOnActiveProject(projectId, userId, ProjectRole.OWNER);
        ProjectMember member = requireMember(projectId, memberId);
        member.changeRole(newRole);
        log.info("Member role changed projectId={} memberId={} role={}", projectId, memberId, newRole);
        return MemberView.of(member, users.displayNames(Set.of(memberId)));
    }

    @Transactional
    public void remove(UUID projectId, UUID memberId, UUID userId) {
        access.requireRoleOnActiveProject(projectId, userId, ProjectRole.OWNER);
        ProjectMember member = requireMember(projectId, memberId);
        member.requireRemovable();
        members.delete(member);
        log.info("Member removed projectId={} memberId={}", projectId, memberId);
    }

    /** Allowed on archived projects too: leaving only changes the member's own list. */
    @Transactional
    public void leave(UUID projectId, UUID userId) {
        access.requireRole(projectId, userId, ProjectRole.LISTENER);
        ProjectMember member = requireMember(projectId, userId);
        member.requireRemovable();
        members.delete(member);
        log.info("Member left projectId={} userId={}", projectId, userId);
    }

    private ProjectMember requireMember(UUID projectId, UUID memberId) {
        return members.findMembership(projectId, memberId).orElseThrow(() -> NotFoundException.of("member", memberId));
    }
}
