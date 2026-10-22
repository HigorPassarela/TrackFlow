package dev.higor.trackflow.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Default profile: the controller bean does not exist, so the route is just an unknown path. */
@WebMvcTest(DevLoginController.class)
class DevLoginDisabledTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void answersNotFoundOutsideTheLocalProfile() {
        var result = mvc.post()
                .uri("/api/dev/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"produtor-a\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }
}
