package dev.higor.trackflow.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rules hold before any controller runs, so these requests target routes without a handler:
 * reaching the handler lookup at all (404) proves security let the request through.
 */
@WebMvcTest(SecurityRulesTest.NoRoutesController.class)
class SecurityRulesTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void answersUnauthenticatedApiCallWithJsonProblem() {
        var result = mvc.get().uri("/api/projects").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("unauthenticated");
    }

    @Test
    void redirectsUnauthenticatedBrowserNavigationToLogin() {
        var result = mvc.get().uri("/some-page").accept(MediaType.TEXT_HTML).exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login");
    }

    @Test
    void rejectsStateChangeWithoutCsrfToken() {
        var result = mvc.post()
                .uri("/api/projects")
                .with(oauth2Login())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Ibiza Vibes\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("invalid-csrf-token");
    }

    @Test
    void letsStateChangeWithCsrfTokenThrough() {
        var result = mvc.post()
                .uri("/api/projects")
                .with(oauth2Login())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Ibiza Vibes\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void keepsPublicRoutesOpen() {
        assertThat(mvc.get().uri("/api/listen/any-token")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/v3/api-docs").exchange().getResponse().getStatus())
                .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    /** Keeps the slice free of the application's controllers; the rules are tested on their own. */
    @RestController
    static class NoRoutesController {}
}
