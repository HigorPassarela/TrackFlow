package dev.higor.trackflow.api.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(GlobalExceptionHandlerTest.ProbeController.class)
@Import(GlobalExceptionHandlerTest.ProbeController.class)
class GlobalExceptionHandlerTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    MockMvcTester mvc;

    @Test
    void mapsValidationErrorsToBadRequestWithFieldList() {
        var result = mvc.post()
                .uri("/probe/validation")
                .with(oauth2Login())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \"x\"}")
                .exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "validation-failed", "/probe/validation");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("displayName");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isNotNull();
    }

    @Test
    void mapsPermissionDeniedToForbidden() {
        var result = mvc.get().uri("/probe/forbidden").with(oauth2Login()).exchange();

        assertProblem(result, HttpStatus.FORBIDDEN, "insufficient-role", "/probe/forbidden");
    }

    @Test
    void mapsNotFoundToNotFound() {
        var result = mvc.get().uri("/probe/not-found").with(oauth2Login()).exchange();

        assertProblem(result, HttpStatus.NOT_FOUND, "project-not-found", "/probe/not-found");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("project " + PROJECT_ID + " not found");
    }

    @Test
    void mapsBusinessRuleToConflict() {
        var result = mvc.get().uri("/probe/conflict").with(oauth2Login()).exchange();

        assertProblem(result, HttpStatus.CONFLICT, "project-archived", "/probe/conflict");
    }

    @Test
    void mapsInvalidInputToBadRequest() {
        var result = mvc.get().uri("/probe/invalid-input").with(oauth2Login()).exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "file-too-large", "/probe/invalid-input");
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code, String instance) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("https://trackflow.dev/errors/" + code);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo(status.getReasonPhrase());
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo(instance);
    }

    @RestController
    static class ProbeController {

        @PostMapping("/probe/validation")
        void validation(@Valid @RequestBody ProbeRequest request) {}

        @GetMapping("/probe/forbidden")
        void forbidden() {
            throw new PermissionDeniedException("Listeners cannot upload versions");
        }

        @GetMapping("/probe/not-found")
        void notFound() {
            throw NotFoundException.of("project", PROJECT_ID);
        }

        @GetMapping("/probe/invalid-input")
        void invalidInput() {
            throw new InvalidInputException("file-too-large", "Files are limited to 1 GB");
        }

        @GetMapping("/probe/conflict")
        void conflict() {
            throw new BusinessRuleException("project-archived", "The project is archived");
        }
    }

    record ProbeRequest(@NotBlank @Size(min = 2, max = 60) String displayName) {}
}
