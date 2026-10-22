package dev.higor.trackflow.api.version.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.version.application.ProjectEventHub;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(ProjectEventsController.class)
class ProjectEventsControllerTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000f");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProjectEventHub hub;

    @Test
    @DisplayName("Não membro tenta se conectar: recurso inexistente")
    void answersNotFoundToNonMember() {
        given(hub.subscribe(PROJECT, USER)).willThrow(NotFoundException.of("project", PROJECT));

        var result = mvc.get()
                .uri("/api/projects/{projectId}/events", PROJECT)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .with(signedIn())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void opensTheStreamForMembers() {
        given(hub.subscribe(PROJECT, USER)).willReturn(new SseEmitter());

        var result = mvc.get()
                .uri("/api/projects/{projectId}/events", PROJECT)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .with(signedIn())
                .asyncExchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    void requiresSession() {
        assertThat(mvc.get().uri("/api/projects/{projectId}/events", PROJECT)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private static OAuth2LoginRequestPostProcessor signedIn() {
        return oauth2Login().attributes(attributes -> {
            attributes.put(AuthenticatedUser.USER_ID_ATTRIBUTE, USER.toString());
            attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "Visitante");
        });
    }
}
