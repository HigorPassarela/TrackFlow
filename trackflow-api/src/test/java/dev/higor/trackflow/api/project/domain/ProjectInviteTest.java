package dev.higor.trackflow.api.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectInviteTest {

    private static final Instant CREATED = Instant.parse("2026-10-21T22:00:00Z");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @Test
    void expiresSevenDaysAfterCreation() {
        assertThat(invite().getExpiresAt()).isEqualTo(CREATED.plus(Duration.ofDays(7)));
    }

    @Test
    void acceptingConsumesTheInvite() {
        ProjectInvite invite = invite();

        invite.accept(GUEST, CREATED.plusSeconds(60));

        assertThat(invite.getAcceptedBy()).isEqualTo(GUEST);
        assertThat(invite.isPending(CREATED.plusSeconds(61))).isFalse();
    }

    @Test
    void refusesExpiredInvite() {
        ProjectInvite invite = invite();

        assertThatThrownBy(() -> invite.accept(GUEST, CREATED.plus(Duration.ofDays(7))))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("invite-expired"));
    }

    @Test
    void refusesInviteUsedBySomeoneElse() {
        ProjectInvite invite = invite();
        invite.accept(UUID.randomUUID(), CREATED.plusSeconds(60));

        assertThatThrownBy(() -> invite.accept(GUEST, CREATED.plusSeconds(120)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("invite-not-available"));
    }

    @Test
    void refusesRevokedInvite() {
        ProjectInvite invite = invite();
        invite.revoke(CREATED.plusSeconds(30));

        assertThatThrownBy(() -> invite.accept(GUEST, CREATED.plusSeconds(60)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("invite-not-available"));
    }

    @Test
    void cannotRevokeAnInviteThatWasUsed() {
        ProjectInvite invite = invite();
        invite.accept(GUEST, CREATED.plusSeconds(60));

        assertThatThrownBy(() -> invite.revoke(CREATED.plusSeconds(90))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void neverInvitesAnOwner() {
        assertThatThrownBy(() -> ProjectInvite.builder()
                        .projectId(PROJECT)
                        .tokenHash("x".repeat(64))
                        .role(ProjectRole.OWNER)
                        .createdBy(ProjectFixtures.OWNER)
                        .createdAt(CREATED)
                        .build())
                .isInstanceOf(BusinessRuleException.class);
    }

    private static ProjectInvite invite() {
        return ProjectInvite.builder()
                .projectId(PROJECT)
                .tokenHash("a".repeat(64))
                .role(ProjectRole.LISTENER)
                .createdBy(ProjectFixtures.OWNER)
                .createdAt(CREATED)
                .build();
    }
}
