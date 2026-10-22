package dev.higor.trackflow.api.version.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.InvalidInputException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
import dev.higor.trackflow.api.shared.web.PageResponse;
import dev.higor.trackflow.api.storage.SignedRequest;
import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.application.EditVersion;
import dev.higor.trackflow.api.version.application.MediaLinks;
import dev.higor.trackflow.api.version.application.RequestUpload;
import dev.higor.trackflow.api.version.application.UploadTicket;
import dev.higor.trackflow.api.version.application.VersionService;
import dev.higor.trackflow.api.version.application.VersionView;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(VersionController.class)
class VersionControllerTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final String VERSIONS = "/api/projects/{projectId}/versions";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private VersionService service;

    @Test
    void returnsPendingVersionAndSignedUploads() {
        given(service.requestUpload(any()))
                .willReturn(new UploadTicket(
                        view(VersionStatus.PENDING_UPLOAD, null, null),
                        signed("source.wav", Map.of("Content-Type", "audio/wav")),
                        signed("project.als", Map.of())));

        var result = postUpload("""
                {"fileName": "bounce.wav", "contentType": "audio/wav", "sizeBytes": 125829120,
                 "ableton": {"fileName": "ibiza.als", "sizeBytes": 2097152}}
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.version.status").isEqualTo("pending_upload");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.upload.headers.Content-Type")
                .isEqualTo("audio/wav");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.abletonUpload.url")
                .asString()
                .endsWith("project.als");
    }

    @Test
    void passesTheDeclaredFilesToTheService() {
        given(service.requestUpload(any()))
                .willReturn(new UploadTicket(
                        view(VersionStatus.PENDING_UPLOAD, null, null), signed("source.wav", Map.of()), null));

        postUpload("""
                {"fileName": "bounce.wav", "contentType": "audio/wav", "sizeBytes": 10, "title": "v4"}
                """);

        then(service)
                .should()
                .requestUpload(new RequestUpload(PROJECT, USER, "bounce.wav", "audio/wav", 10, null, "v4", null));
    }

    @Test
    void rejectsUploadWithoutFileName() {
        var result = postUpload("""
                {"contentType": "audio/wav", "sizeBytes": 10}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("fileName");
        then(service).should(never()).requestUpload(any());
    }

    @Test
    void rejectsEmptyFile() {
        var result = postUpload("""
                {"fileName": "bounce.wav", "contentType": "audio/wav", "sizeBytes": 0}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("sizeBytes");
    }

    @Test
    void reportsFileTooLargeAsBadRequest() {
        given(service.requestUpload(any()))
                .willThrow(new InvalidInputException("file-too-large", "Files are limited to 1 GB"));

        var result = postUpload("""
                {"fileName": "bounce.wav", "contentType": "audio/wav", "sizeBytes": 1610612736}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("file-too-large");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Files are limited to 1 GB");
    }

    @Test
    void reportsListenerUploadAsForbidden() {
        given(service.requestUpload(any())).willThrow(new PermissionDeniedException("Listeners cannot upload"));

        var result = postUpload("""
                {"fileName": "bounce.wav", "contentType": "audio/wav", "sizeBytes": 10}
                """);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void reportsArchivedProjectAsConflict() {
        given(service.requestUpload(any()))
                .willThrow(new BusinessRuleException("project-archived", "The project is archived and read-only"));

        var result = postUpload("""
                {"fileName": "bounce.wav", "contentType": "audio/wav", "sizeBytes": 10}
                """);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("project-archived");
    }

    @Test
    void confirmsVersion() {
        given(service.confirm(PROJECT, VERSION, USER)).willReturn(view(VersionStatus.UPLOADED, 4, null));

        var result = mvc.post()
                .uri(VERSIONS + "/{versionId}/confirm", PROJECT, VERSION)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.number").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("uploaded");
    }

    @Test
    void listsHistoryWithMetricsOnlyWhenReady() {
        given(service.history(PROJECT, USER, 0, 20))
                .willReturn(new PageResponse<>(
                        List.of(view(VersionStatus.PROCESSING, 5, null), view(VersionStatus.READY, 4, 124.0)),
                        0,
                        20,
                        2));

        var result = mvc.get().uri(VERSIONS, PROJECT).with(signedIn()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[0].status").isEqualTo("processing");
        assertThat(result).bodyJson().extractingPath("$.items[0].metrics").isNull();
        assertThat(result).bodyJson().extractingPath("$.items[1].metrics.bpm").isEqualTo(124.0);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[1].metrics.bpmSource")
                .isEqualTo("estimated");
    }

    @Test
    void returnsNotFoundForNonMember() {
        given(service.get(PROJECT, VERSION, USER)).willThrow(NotFoundException.of("project", PROJECT));

        assertThat(mvc.get().uri(VERSIONS + "/{versionId}", PROJECT, VERSION).with(signedIn()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void editsNotesAndBpm() {
        given(service.edit(new EditVersion(PROJECT, VERSION, USER, null, "kick novo", 126.0)))
                .willReturn(view(VersionStatus.READY, 4, 63.0));

        var result = patch("""
                {"notes": "kick novo", "bpm": 126}
                """);

        assertThat(result).hasStatusOk();
    }

    @Test
    void rejectsBpmOutsideFortyToTwoHundredFifty() {
        var result = patch("""
                {"bpm": 300}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("bpm");
        then(service).should(never()).edit(any());
    }

    @Test
    void rejectsTitleLongerThanEightyCharacters() {
        var result = patch("{\"title\": \"" + "x".repeat(81) + "\"}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("title");
    }

    @Test
    void reportsListenerEditAsForbidden() {
        given(service.edit(any())).willThrow(new PermissionDeniedException("Listeners cannot edit"));

        assertThat(patch("{\"title\": \"v4\"}")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void returnsPlaybackLinks() {
        given(service.playback(PROJECT, VERSION, USER))
                .willReturn(new MediaLinks(signed("preview.mp3", Map.of()), signed("peaks.json", Map.of())));

        var result = mvc.get()
                .uri(VERSIONS + "/{versionId}/playback", PROJECT, VERSION)
                .with(signedIn())
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.previewUrl").asString().endsWith("preview.mp3");
        assertThat(result).bodyJson().extractingPath("$.peaksUrl").asString().endsWith("peaks.json");
    }

    @Test
    void reportsPlaybackOfVersionNotReadyAsConflict() {
        given(service.playback(PROJECT, VERSION, USER))
                .willThrow(new BusinessRuleException("version-not-ready", "The version is not ready yet"));

        var result = mvc.get()
                .uri(VERSIONS + "/{versionId}/playback", PROJECT, VERSION)
                .with(signedIn())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("version-not-ready");
    }

    @Test
    void reportsListenerAskingForOriginalAsForbidden() {
        given(service.download(PROJECT, VERSION, USER)).willThrow(new PermissionDeniedException("Editors only"));

        assertThat(mvc.get()
                        .uri(VERSIONS + "/{versionId}/download", PROJECT, VERSION)
                        .with(signedIn()))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void acceptsReprocessing() {
        given(service.reprocess(PROJECT, VERSION, USER)).willReturn(view(VersionStatus.UPLOADED, 4, null));

        var result = mvc.post()
                .uri(VERSIONS + "/{versionId}/reprocess", PROJECT, VERSION)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("uploaded");
    }

    @Test
    void reportsReprocessOfReadyVersionAsConflict() {
        given(service.reprocess(PROJECT, VERSION, USER))
                .willThrow(new BusinessRuleException("version-not-failed", "Only failed versions can be reprocessed"));

        var result = mvc.post()
                .uri(VERSIONS + "/{versionId}/reprocess", PROJECT, VERSION)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("version-not-failed");
    }

    private MvcTestResult postUpload(String body) {
        return mvc.post()
                .uri(VERSIONS + "/uploads", PROJECT)
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult patch(String body) {
        return mvc.patch()
                .uri(VERSIONS + "/{versionId}", PROJECT, VERSION)
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static SignedRequest signed(String name, Map<String, String> headers) {
        return new SignedRequest(URI.create("https://storage.test/" + name), headers, NOW.plusSeconds(900));
    }

    private static VersionView view(VersionStatus status, @Nullable Integer number, @Nullable Double bpm) {
        boolean ready = status == VersionStatus.READY;
        return new VersionView(
                VERSION,
                PROJECT,
                number,
                status,
                null,
                null,
                USER,
                "Editora",
                "bounce.wav",
                10,
                false,
                ready ? 360_000L : null,
                ready ? bpm : null,
                null,
                ready ? -9.4 : null,
                ready ? 0.8 : null,
                null,
                NOW,
                number != null ? NOW : null);
    }

    private static OAuth2LoginRequestPostProcessor signedIn() {
        return oauth2Login().attributes(attributes -> {
            attributes.put(AuthenticatedUser.USER_ID_ATTRIBUTE, USER.toString());
            attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "Editora");
        });
    }
}
