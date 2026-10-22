package dev.higor.trackflow.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(AuthenticatedUserArgumentResolverTest.WhoAmIController.class)
@Import(AuthenticatedUserArgumentResolverTest.WhoAmIController.class)
class AuthenticatedUserArgumentResolverTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Autowired
    private MockMvcTester mvc;

    @Test
    void injectsTheSignedInUserIntoControllers() {
        var result = mvc.get()
                .uri("/api/probe/whoami")
                .with(oauth2Login().attributes(attributes -> {
                    attributes.put(AuthenticatedUser.USER_ID_ATTRIBUTE, USER.toString());
                    attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "VERXT");
                }))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(USER.toString());
        assertThat(result).bodyJson().extractingPath("$.displayName").isEqualTo("VERXT");
    }

    @Test
    void answersUnauthorizedWhenPrincipalHasNoTrackflowUser() {
        var result = mvc.get().uri("/api/probe/whoami").with(oauth2Login()).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("unauthenticated");
    }

    @RestController
    static class WhoAmIController {

        @GetMapping("/api/probe/whoami")
        AuthenticatedUser whoAmI(AuthenticatedUser user) {
            return user;
        }
    }
}
