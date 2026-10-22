package dev.higor.trackflow.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticates through a real HTTP session, the way a browser does, instead of per request. */
@WebMvcTest(LogoutTest.ProbeController.class)
@Import(LogoutTest.ProbeController.class)
class LogoutTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void treatsTheOldSessionAsUnauthenticatedAfterLogout() {
        MockHttpSession session = signedInSession();
        assertThat(mvc.get().uri("/api/probe").session(session)).hasStatusOk();

        var logout = mvc.post().uri("/logout").session(session).with(csrf()).exchange();

        assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(session.isInvalid()).isTrue();
        MockHttpSession sameCookie = new MockHttpSession(null, session.getId());
        assertThat(mvc.get().uri("/api/probe").session(sameCookie)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private static MockHttpSession signedInSession() {
        var principal = new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                Map.of(
                        AuthenticatedUser.USER_ID_ATTRIBUTE, "00000000-0000-0000-0000-00000000000a",
                        AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "VERXT"),
                AuthenticatedUser.USER_ID_ATTRIBUTE);
        var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "github");
        var session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/probe")
        String probe(AuthenticatedUser user) {
            return user.displayName();
        }
    }
}
