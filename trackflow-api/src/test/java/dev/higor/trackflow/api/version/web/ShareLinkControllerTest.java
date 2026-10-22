package dev.higor.trackflow.api.version.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.storage.SignedRequest;
import dev.higor.trackflow.api.version.application.CreatedShareLink;
import dev.higor.trackflow.api.version.application.ListenView;
import dev.higor.trackflow.api.version.application.ShareLinkService;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ShareLinkController.class)
class ShareLinkControllerTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID LINK = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ShareLinkService service;

    @Test
    void createsLinkWithDefaultValidity() {
        given(service.create(PROJECT, VERSION, USER, null))
                .willReturn(new CreatedShareLink(LINK, VERSION, NOW.plusSeconds(7 * 86_400), "tok"));

        var result = mvc.post()
                .uri("/api/projects/{projectId}/versions/{versionId}/share-links", PROJECT, VERSION)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.listenPath").isEqualTo("/api/listen/tok");
    }

    @Test
    void rejectsValidityAboveThirtyDays() {
        var result = mvc.post()
                .uri("/api/projects/{projectId}/versions/{versionId}/share-links", PROJECT, VERSION)
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"validDays\": 31}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("validDays");
        then(service).should(never()).create(any(), any(), any(), any());
    }

    @Test
    void revokesWithNoContent() {
        var result = mvc.delete()
                .uri("/api/projects/{projectId}/share-links/{linkId}", PROJECT, LINK)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        then(service).should().revoke(PROJECT, LINK, USER);
    }

    @Test
    void visitorWithoutSessionListensAndSeesNoComments() throws Exception {
        given(service.listen("tok"))
                .willReturn(new ListenView(
                        "Ibiza Vibes",
                        4,
                        "VERXT",
                        360_000L,
                        new SignedRequest(URI.create("https://storage.test/preview.mp3"), Map.of(), NOW),
                        new SignedRequest(URI.create("https://storage.test/peaks.json"), Map.of(), NOW)));

        var result = mvc.get().uri("/api/listen/{token}", "tok").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.projectTitle").isEqualTo("Ibiza Vibes");
        assertThat(result).bodyJson().extractingPath("$.versionNumber").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.authorName").isEqualTo("VERXT");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("comment", "versions");
    }

    @Test
    void expiredOrRevokedLinkAnswersNotFound() {
        given(service.listen("old"))
                .willThrow(new NotFoundException("share-link-not-found", "This listening link does not exist"));

        var result = mvc.get().uri("/api/listen/{token}", "old").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("share-link-not-found");
    }

    private static OAuth2LoginRequestPostProcessor signedIn() {
        return oauth2Login().attributes(attributes -> {
            attributes.put(AuthenticatedUser.USER_ID_ATTRIBUTE, USER.toString());
            attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "Editora");
        });
    }
}
