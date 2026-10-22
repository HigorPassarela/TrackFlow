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
import dev.higor.trackflow.api.project.ProjectActivity;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.InvalidInputException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
import dev.higor.trackflow.api.storage.InMemoryObjectStorage;
import dev.higor.trackflow.api.version.ReadyMedia;
import dev.higor.trackflow.api.version.VersionConfirmed;
import dev.higor.trackflow.api.version.VersionReprocessRequested;
import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import dev.higor.trackflow.api.version.domain.VersionNumbers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

/**
 * Uploads go through {@link InMemoryObjectStorage}, so each test reads like the real sequence:
 * request the upload, let the "client" put the file, confirm. No container involved.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VersionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EDITOR = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID LISTENER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final long MB = 1024L * 1024;
    private static final VersionProperties PROPERTIES = new VersionProperties(
            new VersionProperties.Upload(DataSize.ofGigabytes(1), DataSize.ofMegabytes(50), Duration.ofMinutes(15)),
            new VersionProperties.Playback(Duration.ofHours(1)),
            new VersionProperties.PendingCleanup(Duration.ofHours(24)));

    @Mock
    private TrackVersionRepository versions;

    @Mock
    private VersionNumbers numbers;

    @Mock
    private ProjectAccess access;

    @Mock
    private ProjectActivity activity;

    @Mock
    private UserDirectory users;

    @Mock
    private ApplicationEventPublisher events;

    private final Map<UUID, TrackVersion> stored = new HashMap<>();
    private final InMemoryObjectStorage storage = new InMemoryObjectStorage(Clock.fixed(NOW, ZoneOffset.UTC));
    private VersionService service;

    @BeforeEach
    void setUp() {
        service = new VersionService(
                versions,
                numbers,
                storage,
                access,
                activity,
                users,
                events,
                PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC));
        given(versions.save(any(TrackVersion.class))).willAnswer(invocation -> {
            TrackVersion version = invocation.getArgument(0);
            if (version.getId() == null) {
                ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
            }
            stored.put(version.getId(), version);
            return version;
        });
        given(versions.findByIdAndProjectId(any(), any()))
                .willAnswer(invocation -> Optional.ofNullable(stored.get(invocation.<UUID>getArgument(0)))
                        .filter(version -> version.getProjectId().equals(invocation.getArgument(1))));
        given(users.displayNames(anySet())).willReturn(Map.of(EDITOR, "Editora"));
        given(access.requireRoleOnActiveProject(PROJECT, EDITOR, ProjectRole.EDITOR))
                .willReturn(ProjectRole.EDITOR);
        given(access.requireRole(any(), any(), any())).willReturn(ProjectRole.EDITOR);
        given(access.requireRoleOnActiveProject(PROJECT, LISTENER, ProjectRole.EDITOR))
                .willThrow(new PermissionDeniedException("Listeners cannot upload"));
        given(access.requireRole(PROJECT, LISTENER, ProjectRole.EDITOR))
                .willThrow(new PermissionDeniedException("Listeners cannot download originals"));
        given(numbers.next(PROJECT)).willReturn(4, 5, 6);
    }

    @Nested
    class RequestUploadScenarios {

        @Test
        @DisplayName("Editor solicita envio de WAV: versão aguardando envio e endereço com expiração")
        void createsPendingVersionAndSignsUpload() {
            UploadTicket ticket = service.requestUpload(wav(120 * MB, null));

            assertThat(ticket.version().status()).isEqualTo(VersionStatus.PENDING_UPLOAD);
            assertThat(ticket.version().number()).isNull();
            assertThat(ticket.source().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
            assertThat(ticket.source().headers())
                    .containsEntry("Content-Type", "audio/wav")
                    .containsEntry("Content-Length", String.valueOf(120 * MB));
            assertThat(ticket.source().url().getPath())
                    .endsWith("/projects/%s/versions/%s/source.wav"
                            .formatted(PROJECT, ticket.version().id()));
            assertThat(ticket.ableton()).isNull();
        }

        @Test
        @DisplayName("Ouvinte solicita envio: erro de permissão")
        void listenerCannotUpload() {
            assertThatThrownBy(() -> service.requestUpload(
                            new RequestUpload(PROJECT, LISTENER, "bounce.wav", "audio/wav", MB, null, null, null)))
                    .isInstanceOf(PermissionDeniedException.class);
            then(versions).should(never()).save(any());
        }

        @Test
        @DisplayName("Arquivo grande demais: recusa informando o limite de 1 GB")
        void rejectsFilesAboveOneGigabyte() {
            assertThatThrownBy(() -> service.requestUpload(wav(1536 * MB, null)))
                    .isInstanceOfSatisfying(InvalidInputException.class, error -> {
                        assertThat(error.code()).isEqualTo("file-too-large");
                        assertThat(error.getMessage()).endsWith("1 GB");
                    });
        }

        @Test
        @DisplayName("Formato não suportado: recusa informando os formatos aceitos")
        void rejectsUnsupportedFormatListingTheAcceptedOnes() {
            assertThatThrownBy(() -> service.requestUpload(
                            new RequestUpload(PROJECT, EDITOR, "loop.ogg", "audio/ogg", MB, null, null, null)))
                    .isInstanceOfSatisfying(InvalidInputException.class, error -> {
                        assertThat(error.code()).isEqualTo("unsupported-format");
                        assertThat(error.getMessage()).contains("WAV, AIFF, FLAC, MP3, MP4, MOV, MKV, WEBM");
                    });
        }

        @Test
        void acceptsScreenRecordingVideo() {
            UploadTicket ticket = service.requestUpload(
                    new RequestUpload(PROJECT, EDITOR, "sessao.MP4", "video/mp4", 300 * MB, null, null, null));

            assertThat(ticket.source().url().getPath()).endsWith("source.mp4");
        }

        @Test
        void rejectsContentTypeThatDoesNotMatchTheExtension() {
            assertThatThrownBy(() -> service.requestUpload(
                            new RequestUpload(PROJECT, EDITOR, "bounce.wav", "image/png", MB, null, null, null)))
                    .isInstanceOfSatisfying(
                            InvalidInputException.class,
                            error -> assertThat(error.code()).isEqualTo("unsupported-format"));
        }

        @Test
        @DisplayName("Versão com projeto anexado: um endereço de envio para cada arquivo")
        void signsOneUploadPerFile() {
            UploadTicket ticket =
                    service.requestUpload(wav(120 * MB, new RequestUpload.AbletonAttachment("ibiza.als", 2 * MB)));

            assertThat(ticket.ableton()).isNotNull();
            assertThat(ticket.ableton().url().getPath()).endsWith("/project.als");
            assertThat(ticket.ableton().headers()).containsEntry("Content-Length", String.valueOf(2 * MB));
            assertThat(ticket.version().hasAbletonProject()).isTrue();
        }

        @Test
        @DisplayName("Anexo com extensão errada: só aceita .als")
        void rejectsAttachmentThatIsNotAbleton() {
            assertThatThrownBy(() -> service.requestUpload(
                            wav(120 * MB, new RequestUpload.AbletonAttachment("projeto.zip", 2 * MB))))
                    .isInstanceOfSatisfying(
                            InvalidInputException.class,
                            error -> assertThat(error.code()).isEqualTo("ableton-invalid-extension"));
        }

        @Test
        void rejectsAbletonProjectAboveFiftyMegabytes() {
            assertThatThrownBy(() -> service.requestUpload(
                            wav(120 * MB, new RequestUpload.AbletonAttachment("big.als", 51 * MB))))
                    .isInstanceOfSatisfying(
                            InvalidInputException.class,
                            error -> assertThat(error.code()).isEqualTo("ableton-file-too-large"));
        }
    }

    @Nested
    class ConfirmScenarios {

        @Test
        @DisplayName("Confirmação válida: recebe o próximo número e passa para enviada")
        void confirmsUploadedFile() {
            UploadTicket ticket = service.requestUpload(wav(120 * MB, null));
            uploadAsTheClient(ticket.source().url().getPath(), 120 * MB);

            VersionView confirmed = service.confirm(PROJECT, ticket.version().id(), EDITOR);

            assertThat(confirmed.number()).isEqualTo(4);
            assertThat(confirmed.status()).isEqualTo(VersionStatus.UPLOADED);
            then(activity).should().record(PROJECT);
            then(events)
                    .should()
                    .publishEvent(new VersionConfirmed(
                            confirmed.id(),
                            PROJECT,
                            4,
                            keyOf(ticket.source().url().getPath()),
                            null));
        }

        @Test
        @DisplayName("Arquivo ausente: recusa, mantém aguardando envio e não atribui número")
        void refusesMissingFile() {
            UploadTicket ticket = service.requestUpload(wav(120 * MB, null));

            assertThatThrownBy(() -> service.confirm(PROJECT, ticket.version().id(), EDITOR))
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            error -> assertThat(error.code()).isEqualTo("upload-missing"));
            assertThat(stored.get(ticket.version().id()).getStatus()).isEqualTo(VersionStatus.PENDING_UPLOAD);
            then(numbers).should(never()).next(any());
            then(events).should(never()).publishEvent(any());
        }

        @Test
        @DisplayName("Arquivo divergente: tamanho diferente do declarado")
        void refusesFileWithAnotherSize() {
            UploadTicket ticket = service.requestUpload(wav(120 * MB, null));
            uploadAsTheClient(ticket.source().url().getPath(), 119 * MB);

            assertThatThrownBy(() -> service.confirm(PROJECT, ticket.version().id(), EDITOR))
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            error -> assertThat(error.code()).isEqualTo("upload-size-mismatch"));
            assertThat(stored.get(ticket.version().id()).getNumber()).isNull();
        }

        @Test
        void requiresTheAbletonProjectToo() {
            UploadTicket ticket =
                    service.requestUpload(wav(120 * MB, new RequestUpload.AbletonAttachment("ibiza.als", 2 * MB)));
            uploadAsTheClient(ticket.source().url().getPath(), 120 * MB);

            assertThatThrownBy(() -> service.confirm(PROJECT, ticket.version().id(), EDITOR))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Confirmação repetida: mesma versão, sem novo número nem novo processamento")
        void repeatedConfirmationChangesNothing() {
            UploadTicket ticket = service.requestUpload(wav(120 * MB, null));
            uploadAsTheClient(ticket.source().url().getPath(), 120 * MB);
            service.confirm(PROJECT, ticket.version().id(), EDITOR);

            VersionView again = service.confirm(PROJECT, ticket.version().id(), EDITOR);

            assertThat(again.number()).isEqualTo(4);
            then(numbers).should().next(PROJECT);
            then(events).should().publishEvent(any(VersionConfirmed.class));
        }

        @Test
        void versionOfAnotherProjectIsNotFound() {
            UploadTicket ticket = service.requestUpload(wav(120 * MB, null));
            UUID otherProject = UUID.fromString("00000000-0000-0000-0000-000000000099");
            given(access.requireRoleOnActiveProject(otherProject, EDITOR, ProjectRole.EDITOR))
                    .willReturn(ProjectRole.EDITOR);

            assertThatThrownBy(
                            () -> service.confirm(otherProject, ticket.version().id(), EDITOR))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class ReadingScenarios {

        @Test
        void pendingUploadsAreInvisible() {
            UploadTicket ticket = service.requestUpload(wav(120 * MB, null));

            assertThatThrownBy(() -> service.get(PROJECT, ticket.version().id(), EDITOR))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Editar notas: as novas notas aparecem na versão")
        void editorChangesNotesAndBpm() {
            UUID id = confirmedVersion();

            VersionView edited =
                    service.edit(new EditVersion(PROJECT, id, EDITOR, null, "kick novo, sidechain no baixo", 126.0));

            assertThat(edited.notes()).isEqualTo("kick novo, sidechain no baixo");
            assertThat(edited.bpmOverride()).isEqualTo(126.0);
        }

        @Test
        @DisplayName("Ouvinte tenta editar: erro de permissão")
        void listenerCannotEdit() {
            UUID id = confirmedVersion();

            assertThatThrownBy(() -> service.edit(new EditVersion(PROJECT, id, LISTENER, "Outro", null, null)))
                    .isInstanceOf(PermissionDeniedException.class);
        }

        @Test
        @DisplayName("Versão não pronta: recusa a reprodução")
        void refusesPlaybackOfVersionNotReady() {
            UUID id = confirmedVersion();

            assertThatThrownBy(() -> service.playback(PROJECT, id, LISTENER))
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            error -> assertThat(error.code()).isEqualTo("version-not-ready"));
        }

        @Test
        @DisplayName("Membro ouve versão pronta: preview, forma de onda e expiração de 1 hora")
        void signsPreviewAndPeaksForOneHour() {
            UUID id = readyVersion();

            MediaLinks links = service.playback(PROJECT, id, LISTENER);

            assertThat(links.primary().url().getPath()).endsWith("preview.mp3");
            assertThat(links.secondary().url().getPath()).endsWith("peaks.json");
            assertThat(links.primary().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        }

        @Test
        @DisplayName("Editor baixa original: endereço temporário com o nome original")
        void downloadKeepsTheOriginalFileName() {
            UUID id = confirmedVersion();

            MediaLinks links = service.download(PROJECT, id, EDITOR);

            assertThat(links.primary().url().getQuery()).isEqualTo("download=bounce v4.wav");
        }

        @Test
        @DisplayName("Ouvinte pede original: erro de permissão")
        void listenerCannotDownloadOriginal() {
            UUID id = confirmedVersion();

            assertThatThrownBy(() -> service.download(PROJECT, id, LISTENER))
                    .isInstanceOf(PermissionDeniedException.class);
        }
    }

    @Nested
    class ReprocessScenarios {

        @Test
        @DisplayName("Reprocessar falha: volta para enviada e o processamento é disparado de novo")
        void reprocessesFailedVersion() {
            UUID id = confirmedVersion();
            TrackVersion version = stored.get(id);
            UUID job = UUID.randomUUID();
            version.startProcessing(job, NOW);
            version.fail(job, "processing-error", NOW);

            VersionView reprocessed = service.reprocess(PROJECT, id, EDITOR);

            assertThat(reprocessed.status()).isEqualTo(VersionStatus.UPLOADED);
            then(events).should().publishEvent(new VersionReprocessRequested(id, PROJECT, version.getSourceKey()));
        }

        @Test
        @DisplayName("Reprocessar versão pronta: só versões com falha podem ser reprocessadas")
        void refusesToReprocessReadyVersion() {
            UUID id = readyVersion();

            assertThatThrownBy(() -> service.reprocess(PROJECT, id, EDITOR))
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            error -> assertThat(error.code()).isEqualTo("version-not-failed"));
        }
    }

    private UUID confirmedVersion() {
        UploadTicket ticket = service.requestUpload(wav(120 * MB, null));
        uploadAsTheClient(ticket.source().url().getPath(), 120 * MB);
        return service.confirm(PROJECT, ticket.version().id(), EDITOR).id();
    }

    private UUID readyVersion() {
        UUID id = confirmedVersion();
        UUID job = UUID.randomUUID();
        TrackVersion version = stored.get(id);
        version.startProcessing(job, NOW);
        version.complete(
                job,
                new ReadyMedia(
                        "projects/p/versions/v/preview.mp3",
                        "projects/p/versions/v/peaks.json",
                        360_000L,
                        124.0,
                        -9.4,
                        0.8),
                NOW);
        return id;
    }

    private void uploadAsTheClient(String urlPath, long sizeBytes) {
        storage.simulateUpload(keyOf(urlPath), sizeBytes, "audio/wav");
    }

    private static String keyOf(String urlPath) {
        return urlPath.substring(1);
    }

    private static RequestUpload wav(long sizeBytes, RequestUpload.@Nullable AbletonAttachment ableton) {
        return new RequestUpload(PROJECT, EDITOR, "bounce v4.wav", "audio/wav", sizeBytes, ableton, null, null);
    }
}
