package dev.higor.trackflow.api.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.VersionStatusChanged;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectEventHubTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000f");

    @Mock
    private ProjectAccess access;

    @Test
    void checksMembershipBeforeKeepingAConnection() {
        given(access.requireRole(PROJECT, STRANGER, ProjectRole.LISTENER))
                .willThrow(NotFoundException.of("project", PROJECT));
        var hub = new ProjectEventHub(access);

        assertThatThrownBy(() -> hub.subscribe(PROJECT, STRANGER)).isInstanceOf(NotFoundException.class);
        assertThat(hub.subscribers(PROJECT)).isZero();
    }

    @Test
    void keepsOneConnectionPerSubscription() {
        var hub = new ProjectEventHub(access);

        hub.subscribe(PROJECT, MEMBER);
        hub.subscribe(PROJECT, MEMBER);

        assertThat(hub.subscribers(PROJECT)).isEqualTo(2);
        assertThat(hub.subscribers(OTHER_PROJECT)).isZero();
    }

    @Test
    void announcingToAProjectWithoutSubscribersIsHarmless() {
        var hub = new ProjectEventHub(access);

        hub.on(new VersionStatusChanged(UUID.randomUUID(), OTHER_PROJECT, VersionStatus.READY, Instant.EPOCH));

        assertThat(hub.subscribers(OTHER_PROJECT)).isZero();
    }
}
