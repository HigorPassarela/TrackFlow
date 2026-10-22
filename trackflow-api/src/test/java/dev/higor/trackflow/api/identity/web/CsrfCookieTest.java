package dev.higor.trackflow.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kept apart from {@link SecurityRulesTest}: spring-security-test's {@code csrf()} replaces the
 * token repository of the cached context for good, after which no cookie would ever be written.
 */
@WebMvcTest(CsrfCookieTest.PublicProbeController.class)
@Import(CsrfCookieTest.PublicProbeController.class)
class CsrfCookieTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void issuesCsrfTokenCookieReadableByScripts() {
        var result = mvc.get().uri("/api/listen/probe").exchange();

        assertThat(result.getResponse().getCookie("XSRF-TOKEN"))
                .isNotNull()
                .satisfies(cookie -> assertThat(cookie.isHttpOnly()).isFalse());
    }

    /** A successful public response; error responses are rebuilt by MVC and drop earlier headers. */
    @RestController
    static class PublicProbeController {

        @GetMapping("/api/listen/probe")
        String probe() {
            return "ok";
        }
    }
}
