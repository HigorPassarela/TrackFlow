package dev.higor.trackflow.api.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.higor.trackflow.api.identity.domain.User;
import dev.higor.trackflow.api.identity.domain.UserFixtures;
import dev.higor.trackflow.api.identity.domain.UserRepository;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.support.PostgresTestConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
class ProjectRepositoryIT {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private ProjectMemberRepository members;

    @Autowired
    private UserRepository users;

    @Autowired
    private TestEntityManager entityManager;

    private User me;
    private User someoneElse;

    @BeforeEach
    void setUp() {
        me = users.save(UserFixtures.githubUser("me"));
        someoneElse = users.save(UserFixtures.githubUser("someone-else"));
    }

    @Test
    void listsOwnAndThirdPartyProjectsWithMyRoleInEach() {
        Project mine = projectOwnedBy(me, "Minha faixa", T0);
        Project theirs = projectOwnedBy(someoneElse, "Faixa de outra pessoa", T0.plusSeconds(60));
        join(theirs, me, ProjectRole.LISTENER);

        var page = projects.findMine(me.getId(), false, PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(ProjectListItem::id, ProjectListItem::role)
                .containsExactly(tuple(theirs.getId(), ProjectRole.LISTENER), tuple(mine.getId(), ProjectRole.OWNER));
    }

    @Test
    void ordersByLastActivityAndMovesRecentlyActiveProjectToTheTop() {
        Project oldest = projectOwnedBy(me, "Primeira", T0);
        Project newest = projectOwnedBy(me, "Segunda", T0.plusSeconds(60));

        projects.recordActivity(oldest.getId(), T0.plusSeconds(120));
        entityManager.clear();
        var page = projects.findMine(me.getId(), false, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ProjectListItem::id).containsExactly(oldest.getId(), newest.getId());
    }

    @Test
    void leavesArchivedProjectsOutUnlessAsked() {
        Project active = projectOwnedBy(me, "Ativa", T0);
        Project archived = projectOwnedBy(me, "Arquivada", T0.plusSeconds(60));
        archived.archive(T0.plusSeconds(90));
        entityManager.flush();

        var withoutArchived = projects.findMine(me.getId(), false, PageRequest.of(0, 20));
        var withArchived = projects.findMine(me.getId(), true, PageRequest.of(0, 20));

        assertThat(withoutArchived.getContent()).extracting(ProjectListItem::id).containsExactly(active.getId());
        assertThat(withArchived.getTotalElements()).isEqualTo(2);
    }

    @Test
    void ignoresProjectsTheUserIsNotAMemberOf() {
        projectOwnedBy(someoneElse, "Privada", T0);

        assertThat(projects.findMine(me.getId(), true, PageRequest.of(0, 20))).isEmpty();
    }

    private Project projectOwnedBy(User owner, String title, Instant createdAt) {
        Project project = projects.save(Project.builder()
                .title(title)
                .ownerId(owner.getId())
                .createdAt(createdAt)
                .build());
        join(project, owner, ProjectRole.OWNER);
        return project;
    }

    private void join(Project project, User user, ProjectRole role) {
        UUID projectId = project.getId();
        members.save(ProjectFixtures.member(projectId, user.getId(), role));
        entityManager.flush();
    }
}
