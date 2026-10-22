package dev.higor.trackflow.api.project.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.application.AcceptedInvite;
import dev.higor.trackflow.api.project.application.CreatedInvite;
import dev.higor.trackflow.api.project.application.InviteService;
import dev.higor.trackflow.api.project.application.InviteView;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(InviteController.class)
class InviteControllerTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID INVITE = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Instant CREATED = Instant.parse("2026-10-22T10:00:00Z");
    private static final String TOKEN = "s3cr3t-token-value";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private InviteService service;

    @Test
    void returnsTheLinkOnlyWhenCreating() {
        given(service.create(PROJECT, ProjectRole.EDITOR, USER)).willReturn(new CreatedInvite(view(), TOKEN));

        var result = mvc.post()
                .uri("/api/projects/{projectId}/invites", PROJECT)
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"editor\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.acceptPath").isEqualTo("/api/invites/" + TOKEN + "/accept");
        assertThat(result).bodyJson().extractingPath("$.expiresAt").isEqualTo("2026-10-29T10:00:00Z");
    }

    @Test
    @DisplayName("Consulta posterior de convite: papel, criador e expiração, sem o link")
    void listsPendingInvitesWithoutTheLink() throws Exception {
        given(service.listPending(PROJECT, USER)).willReturn(List.of(view()));

        var result = mvc.get()
                .uri("/api/projects/{projectId}/invites", PROJECT)
                .with(signedIn())
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[0].createdBy.name").isEqualTo("Produtora A");
        assertThat(result).bodyJson().extractingPath("$[0].role").isEqualTo("editor");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("token", "acceptPath");
    }

    @Test
    void mapsEditorCreatingInviteToForbidden() {
        given(service.create(PROJECT, ProjectRole.LISTENER, USER))
                .willThrow(new PermissionDeniedException("Only the owner invites"));

        var result = mvc.post()
                .uri("/api/projects/{projectId}/invites", PROJECT)
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"listener\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void revokesWithNoContent() {
        var result = mvc.delete()
                .uri("/api/projects/{projectId}/invites/{inviteId}", PROJECT, INVITE)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        then(service).should().revoke(PROJECT, INVITE, USER);
    }

    @Test
    void acceptsInvite() {
        given(service.accept(TOKEN, USER)).willReturn(new AcceptedInvite(PROJECT, ProjectRole.LISTENER));

        var result = mvc.post()
                .uri("/api/invites/{token}/accept", TOKEN)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("listener");
    }

    @Test
    void explainsExpiredInvite() {
        given(service.accept(TOKEN, USER)).willThrow(new BusinessRuleException("invite-expired", "Expired"));

        var result = mvc.post()
                .uri("/api/invites/{token}/accept", TOKEN)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("invite-expired");
    }

    private static InviteView view() {
        return new InviteView(
                INVITE, ProjectRole.EDITOR, USER, "Produtora A", CREATED, CREATED.plusSeconds(7 * 86_400));
    }

    private static OAuth2LoginRequestPostProcessor signedIn() {
        return oauth2Login().attributes(attributes -> {
            attributes.put(AuthenticatedUser.USER_ID_ATTRIBUTE, USER.toString());
            attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "Produtora A");
        });
    }
}
