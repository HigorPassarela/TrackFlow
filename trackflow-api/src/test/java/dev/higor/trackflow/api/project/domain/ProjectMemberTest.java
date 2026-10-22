package dev.higor.trackflow.api.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectMemberTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void promotesListenerToEditor() {
        ProjectMember member = ProjectFixtures.member(PROJECT, USER, ProjectRole.LISTENER);

        member.changeRole(ProjectRole.EDITOR);

        assertThat(member.getRole()).isEqualTo(ProjectRole.EDITOR);
    }

    @Test
    void ownerCannotChangeOwnRole() {
        ProjectMember owner = ProjectFixtures.member(PROJECT, USER, ProjectRole.OWNER);

        assertThatThrownBy(() -> owner.changeRole(ProjectRole.EDITOR))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("owner-role-cannot-change"));
    }

    @Test
    void nobodyElseBecomesOwner() {
        ProjectMember editor = ProjectFixtures.member(PROJECT, USER, ProjectRole.EDITOR);

        assertThatThrownBy(() -> editor.changeRole(ProjectRole.OWNER))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("single-owner"));
    }

    @Test
    void ownerIsNotRemovable() {
        ProjectMember owner = ProjectFixtures.member(PROJECT, USER, ProjectRole.OWNER);

        assertThatThrownBy(owner::requireRemovable)
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("owner-cannot-leave"));
    }
}
