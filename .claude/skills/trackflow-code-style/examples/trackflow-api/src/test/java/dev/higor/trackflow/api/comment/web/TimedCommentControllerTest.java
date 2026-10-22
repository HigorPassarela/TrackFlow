package dev.higor.trackflow.api.comment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.comment.application.CommentView;
import dev.higor.trackflow.api.comment.application.TimedCommentService;
import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Covers what belongs to the HTTP layer only: status codes, JSON shape, validation and error
 * mapping. Business rules are tested in the service and domain tests.
 */
@WebMvcTest(TimedCommentController.class)
class TimedCommentControllerTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final String COMMENTS = "/api/projects/{projectId}/versions/{versionId}/comments";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TimedCommentService service;

    @Test
    void createsCommentAndReturnsItWithTheAuthor() {
        given(service.add(any())).willReturn(view(92_000L));

        assertThat(mvc.post().uri(COMMENTS, PROJECT, VERSION)
                        .with(member()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"positionMs": 92000, "body": "kick embolando com o baixo"}
                                """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.author.name")
                .isEqualTo("VERXT");
    }

    @Test
    void rejectsRangeEndingBeforeItStarts() {
        assertThat(mvc.post().uri(COMMENTS, PROJECT, VERSION)
                        .with(member()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"positionMs": 60000, "endPositionMs": 50000, "body": "trecho"}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("rangeValid");
        then(service).should(never()).add(any());
    }

    @Test
    void returnsNotFoundForNonMember() {
        given(service.list(PROJECT, VERSION, USER, false)).willThrow(NotFoundException.of("project", PROJECT));

        assertThat(mvc.get().uri(COMMENTS, PROJECT, VERSION).with(member()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("project-not-found");
    }

    @Test
    void resolveAnswersNoContent() {
        assertThat(mvc.post().uri(COMMENTS + "/{commentId}/resolve", PROJECT, VERSION, UUID.randomUUID())
                        .with(member()).with(csrf()))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    /** A logged-in user as the identity module leaves it in the session after the OAuth2 login. */
    private static OAuth2LoginRequestPostProcessor member() {
        return oauth2Login().attributes(attributes -> {
            attributes.put(AuthenticatedUser.USER_ID_ATTRIBUTE, USER.toString());
            attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "VERXT");
        });
    }

    private static CommentView view(long positionMs) {
        return new CommentView(
                UUID.randomUUID(), positionMs, null, "kick embolando com o baixo",
                USER, "VERXT", Instant.parse("2026-10-21T22:00:00Z"), null, List.of());
    }
}
