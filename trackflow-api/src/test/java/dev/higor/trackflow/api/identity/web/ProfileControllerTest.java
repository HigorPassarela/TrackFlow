package dev.higor.trackflow.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.identity.application.AccountService;
import dev.higor.trackflow.api.identity.application.AccountView;
import dev.higor.trackflow.api.identity.domain.AuthProvider;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ProfileController.class)
class ProfileControllerTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AccountService accounts;

    @Test
    void returnsOwnProfile() {
        given(accounts.profile(USER)).willReturn(view("Produtora A"));

        var result = mvc.get().uri("/api/me").with(signedIn()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.displayName").isEqualTo("Produtora A");
        assertThat(result).bodyJson().extractingPath("$.provider").isEqualTo("github");
    }

    @Test
    void changesDisplayName() {
        given(accounts.rename(USER, "VERXT")).willReturn(view("VERXT"));

        var result = mvc.patch()
                .uri("/api/me")
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \"VERXT\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.displayName").isEqualTo("VERXT");
    }

    @Test
    void rejectsOneCharacterDisplayNamePointingAtTheField() {
        var result = mvc.patch()
                .uri("/api/me")
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \"V\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("displayName");
        then(accounts).should(never()).rename(any(), any());
    }

    @Test
    void rejectsDisplayNameWithSurroundingSpaces() {
        var result = mvc.patch()
                .uri("/api/me")
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \" V \"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void requiresSession() {
        assertThat(mvc.get().uri("/api/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private static OAuth2LoginRequestPostProcessor signedIn() {
        return oauth2Login().attributes(attributes -> {
            attributes.put(AuthenticatedUser.USER_ID_ATTRIBUTE, USER.toString());
            attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "Produtora A");
        });
    }

    private static AccountView view(String displayName) {
        return new AccountView(
                USER, AuthProvider.GITHUB, displayName, null, null, Instant.parse("2026-10-21T22:00:00Z"));
    }
}
