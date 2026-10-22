package dev.higor.trackflow.api.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectDirectory;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
import dev.higor.trackflow.api.shared.security.TokenGenerator;
import dev.higor.trackflow.api.storage.InMemoryObjectStorage;
import dev.higor.trackflow.api.version.domain.ShareLink;
import dev.higor.trackflow.api.version.domain.ShareLinkRepository;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import dev.higor.trackflow.api.version.domain.TrackVersionFixtures;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
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
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShareLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final UUID PROJECT = TrackVersionFixtures.PROJECT;
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID EDITOR = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID OTHER_EDITOR = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final UUID LISTENER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final VersionProperties PROPERTIES = new VersionProperties(
            new VersionProperties.Upload(DataSize.ofGigabytes(1), DataSize.ofMegabytes(50), Duration.ofMinutes(15)),
            new VersionProperties.Playback(Duration.ofHours(1)),
            new VersionProperties.PendingCleanup(Duration.ofHours(24)));

    @Mock
    private ShareLinkRepository links;

    @Mock
    private TrackVersionRepository versions;

    @Mock
    private ProjectAccess access;

    @Mock
    private ProjectDirectory projects;

    @Mock
    private UserDirectory users;

    private final TokenGenerator tokens = new TokenGenerator();
    private final Map<String, ShareLink> linksByHash = new HashMap<>();
    private TrackVersion version;

    @BeforeEach
    void setUp() {
        version = TrackVersionFixtures.ready();
        given(versions.findByIdAndProjectId(version.getId(), PROJECT)).willReturn(Optional.of(version));
        given(versions.findById(version.getId())).willReturn(Optional.of(version));
        given(links.save(any(ShareLink.class))).willAnswer(invocation -> {
            ShareLink link = invocation.getArgument(0);
            ReflectionTestUtils.setField(link, "id", UUID.randomUUID());
            linksByHash.put(link.getTokenHash(), link);
            return link;
        });
        given(links.findByTokenHash(any()))
                .willAnswer(invocation -> Optional.ofNullable(linksByHash.get(invocation.<String>getArgument(0))));
        given(links.findByIdAndProjectId(any(), any()))
                .willAnswer(invocation -> linksByHash.values().stream()
                        .filter(link -> link.getId().equals(invocation.getArgument(0)))
                        .findFirst());
        given(access.requireRole(PROJECT, OWNER, ProjectRole.EDITOR)).willReturn(ProjectRole.OWNER);
        given(access.requireRole(PROJECT, EDITOR, ProjectRole.EDITOR)).willReturn(ProjectRole.EDITOR);
        given(access.requireRole(PROJECT, LISTENER, ProjectRole.EDITOR))
                .willThrow(new PermissionDeniedException("Listeners cannot share"));
        given(access.requireRole(PROJECT, OWNER, ProjectRole.LISTENER)).willReturn(ProjectRole.OWNER);
        given(access.requireRole(PROJECT, EDITOR, ProjectRole.LISTENER)).willReturn(ProjectRole.EDITOR);
        given(access.requireRole(PROJECT, OTHER_EDITOR, ProjectRole.LISTENER)).willReturn(ProjectRole.EDITOR);
        given(projects.findTitle(PROJECT)).willReturn(Optional.of("Ibiza Vibes"));
        given(users.displayNames(anySet())).willReturn(Map.of(TrackVersionFixtures.UPLOADER, "VERXT"));
    }

    @Test
    void editorCreatesLinkValidForSevenDaysByDefault() {
        CreatedShareLink created = serviceAt(NOW).create(PROJECT, version.getId(), EDITOR, null);

        assertThat(created.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(linksByHash).containsOnlyKeys(tokens.hash(created.token()));
    }

    @Test
    void acceptsUpToThirtyDays() {
        CreatedShareLink created = serviceAt(NOW).create(PROJECT, version.getId(), OWNER, 30);

        assertThat(created.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void listenersCannotCreateLinks() {
        assertThatThrownBy(() -> serviceAt(NOW).create(PROJECT, version.getId(), LISTENER, null))
                .isInstanceOf(PermissionDeniedException.class);
        then(links).should(never()).save(any());
    }

    @Test
    @DisplayName("Versão ainda não pronta: recusa informando que não está pronta")
    void refusesLinkForVersionStillProcessing() {
        TrackVersion processing = TrackVersionFixtures.processing(UUID.randomUUID());
        given(versions.findByIdAndProjectId(processing.getId(), PROJECT)).willReturn(Optional.of(processing));

        assertThatThrownBy(() -> serviceAt(NOW).create(PROJECT, processing.getId(), EDITOR, null))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("version-not-ready"));
    }

    @Test
    void ownerRevokesAnyLink() {
        CreatedShareLink created = serviceAt(NOW).create(PROJECT, version.getId(), EDITOR, null);

        serviceAt(NOW).revoke(PROJECT, created.id(), OWNER);

        assertThat(linksByHash.get(tokens.hash(created.token())).getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void creatorRevokesOwnLink() {
        CreatedShareLink created = serviceAt(NOW).create(PROJECT, version.getId(), EDITOR, null);

        serviceAt(NOW).revoke(PROJECT, created.id(), EDITOR);

        assertThat(linksByHash.get(tokens.hash(created.token())).getRevokedAt()).isNotNull();
    }

    @Test
    void anotherEditorCannotRevokeSomeoneElsesLink() {
        CreatedShareLink created = serviceAt(NOW).create(PROJECT, version.getId(), EDITOR, null);

        assertThatThrownBy(() -> serviceAt(NOW).revoke(PROJECT, created.id(), OTHER_EDITOR))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("Visitante abre link válido: título, número, autor, reprodução e forma de onda")
    void visitorListensThroughValidLink() {
        CreatedShareLink created = serviceAt(NOW).create(PROJECT, version.getId(), EDITOR, null);

        ListenView view = serviceAt(NOW.plus(Duration.ofDays(6))).listen(created.token());

        assertThat(view.projectTitle()).isEqualTo("Ibiza Vibes");
        assertThat(view.versionNumber()).isEqualTo(version.getNumber());
        assertThat(view.authorName()).isEqualTo("VERXT");
        assertThat(view.preview().url().getPath()).endsWith("preview.mp3");
        assertThat(view.peaks().url().getPath()).endsWith("peaks.json");
    }

    @Test
    @DisplayName("Link expirado: recurso inexistente")
    void expiredLinkIsNotFound() {
        CreatedShareLink created = serviceAt(NOW).create(PROJECT, version.getId(), EDITOR, 1);

        assertThatThrownBy(() -> serviceAt(NOW.plus(Duration.ofDays(1))).listen(created.token()))
                .isInstanceOfSatisfying(
                        NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("share-link-not-found"));
    }

    @Test
    @DisplayName("Link revogado: recurso inexistente")
    void revokedLinkIsNotFound() {
        CreatedShareLink created = serviceAt(NOW).create(PROJECT, version.getId(), EDITOR, null);
        serviceAt(NOW).revoke(PROJECT, created.id(), EDITOR);

        assertThatThrownBy(() -> serviceAt(NOW).listen(created.token())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownTokenIsNotFound() {
        assertThatThrownBy(() -> serviceAt(NOW).listen("made-up")).isInstanceOf(NotFoundException.class);
    }

    private ShareLinkService serviceAt(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new ShareLinkService(
                links, versions, new InMemoryObjectStorage(clock), access, projects, users, tokens, PROPERTIES, clock);
    }
}
