package dev.higor.trackflow.api.comment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.comment.application.CommentView;
import dev.higor.trackflow.api.comment.application.TimedCommentService;
import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
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

/**
 * Covers what belongs to the HTTP layer only: status codes, JSON shape, validation and error
 * mapping. Business rules are tested in the service and domain tests.
 */
@WebMvcTest(TimedCommentController.class)
class TimedCommentControllerTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID COMMENT = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String COMMENTS = "/api/projects/{projectId}/versions/{versionId}/comments";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TimedCommentService service;

    @Test
    void createsCommentAndReturnsItWithTheAuthor() {
        given(service.add(any())).willReturn(view(92_000L));

        assertThat(mvc.post()
                        .uri(COMMENTS, PROJECT, VERSION)
                        .with(member())
                        .with(csrf())
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
        assertThat(mvc.post()
                        .uri(COMMENTS, PROJECT, VERSION)
                        .with(member())
                        .with(csrf())
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
        assertThat(mvc.post()
                        .uri(COMMENTS + "/{commentId}/resolve", PROJECT, VERSION, UUID.randomUUID())
                        .with(member())
                        .with(csrf()))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("Scenario: range comment")
    void createsRangeCommentWithStartAndEnd() {
        given(service.add(any())).willReturn(view(60_000L, 75_000L, false));

        var body = assertThat(mvc.post()
                        .uri(COMMENTS, PROJECT, VERSION)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"positionMs": 60000, "endPositionMs": 75000, "body": "drop sem energia"}
                                """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson();

        body.extractingPath("$.positionMs").isEqualTo(60000);
        body.extractingPath("$.endPositionMs").isEqualTo(75000);
    }

    @Test
    @DisplayName("Scenario: position beyond the track")
    void rejectsPositionBeyondTheTrackWithTheDuration() {
        given(service.add(any()))
                .willThrow(new BusinessRuleException(
                        "position-beyond-duration", "Position 400000 ms is beyond the version duration of 360000 ms"));

        var body = assertThat(mvc.post()
                        .uri(COMMENTS, PROJECT, VERSION)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"positionMs": 400000, "body": "fim"}
                                """))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson();

        body.extractingPath("$.code").isEqualTo("position-beyond-duration");
        body.extractingPath("$.detail").asString().contains("360000");
    }

    @Test
    @DisplayName("Scenario: version still processing")
    void rejectsCommentOnVersionThatIsNotReady() {
        given(service.add(any())).willThrow(new BusinessRuleException("version-not-ready", "Version 4 is not ready"));

        assertThat(mvc.post()
                        .uri(COMMENTS, PROJECT, VERSION)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"positionMs": 1000, "body": "cedo demais"}
                                """))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("version-not-ready");
    }

    @Test
    @DisplayName("Scenario: direct reply")
    void repliesToAComment() {
        given(service.reply(any())).willReturn(view(92_000L));

        assertThat(mvc.post()
                        .uri(COMMENTS + "/{commentId}/replies", PROJECT, VERSION, COMMENT)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "corrigi na v5"}
                                """))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Scenario: author edits")
    void editReturnsTheCommentMarkedAsEdited() {
        given(service.edit(any())).willReturn(view(92_000L, null, true));

        assertThat(mvc.patch()
                        .uri(COMMENTS + "/{commentId}", PROJECT, VERSION, COMMENT)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "kick corrigido"}
                                """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.edited")
                .isEqualTo(true);
    }

    @Test
    void rejectsEditWithEmptyText() {
        assertThat(mvc.patch()
                        .uri(COMMENTS + "/{commentId}", PROJECT, VERSION, COMMENT)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "  "}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("body");
        then(service).should(never()).edit(any());
    }

    @Test
    @DisplayName("Scenario: another member tries to edit")
    void editBySomeoneElseIsForbidden() {
        given(service.edit(any())).willThrow(new PermissionDeniedException("Only the author can edit a comment"));

        assertThat(mvc.patch()
                        .uri(COMMENTS + "/{commentId}", PROJECT, VERSION, COMMENT)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "outro texto"}
                                """))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteAnswersNoContent() {
        assertThat(mvc.delete()
                        .uri(COMMENTS + "/{commentId}", PROJECT, VERSION, COMMENT)
                        .with(member())
                        .with(csrf()))
                .hasStatus(HttpStatus.NO_CONTENT);
        then(service).should().delete(PROJECT, VERSION, COMMENT, USER);
    }

    @Test
    void deleteBySomeoneElseIsForbidden() {
        willThrow(new PermissionDeniedException("Only the author and the owner can delete a comment"))
                .given(service)
                .delete(PROJECT, VERSION, COMMENT, USER);

        assertThat(mvc.delete()
                        .uri(COMMENTS + "/{commentId}", PROJECT, VERSION, COMMENT)
                        .with(member())
                        .with(csrf()))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void reopenAnswersNoContent() {
        assertThat(mvc.post()
                        .uri(COMMENTS + "/{commentId}/reopen", PROJECT, VERSION, COMMENT)
                        .with(member())
                        .with(csrf()))
                .hasStatus(HttpStatus.NO_CONTENT);
        then(service).should().reopen(PROJECT, VERSION, COMMENT, USER);
    }

    @Test
    @DisplayName("Scenario: listener tries to resolve another person's comment")
    void resolveWithoutPermissionIsForbidden() {
        willThrow(new PermissionDeniedException("Only the author, editors and the owner can resolve a comment"))
                .given(service)
                .resolve(PROJECT, VERSION, COMMENT, USER);

        assertThat(mvc.post()
                        .uri(COMMENTS + "/{commentId}/resolve", PROJECT, VERSION, COMMENT)
                        .with(member())
                        .with(csrf()))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void listShowsRemovedCommentsWithoutTextAndWhoResolved() {
        UUID resolver = UUID.fromString("00000000-0000-0000-0000-00000000000e");
        CommentView removed = new CommentView(
                COMMENT,
                30_000L,
                null,
                null,
                USER,
                "VERXT",
                Instant.parse("2026-10-21T22:00:00Z"),
                false,
                true,
                Instant.parse("2026-10-21T23:00:00Z"),
                resolver,
                "Parceira",
                List.of(view(30_000L)));
        given(service.list(PROJECT, VERSION, USER, false)).willReturn(List.of(removed));

        var body = assertThat(mvc.get().uri(COMMENTS, PROJECT, VERSION).with(member()))
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$[0].removed").isEqualTo(true);
        body.extractingPath("$[0].body").isNull();
        body.extractingPath("$[0].resolution.by.name").isEqualTo("Parceira");
        body.extractingPath("$[0].replies.length()").isEqualTo(1);
    }

    @Test
    void listCanBeFilteredToUnresolvedComments() {
        given(service.list(PROJECT, VERSION, USER, true)).willReturn(List.of());

        assertThat(mvc.get()
                        .uri(COMMENTS, PROJECT, VERSION)
                        .param("onlyUnresolved", "true")
                        .with(member()))
                .hasStatusOk();
        then(service).should().list(PROJECT, VERSION, USER, true);
    }

    /** A logged-in user as the identity module leaves it in the session after the OAuth2 login. */
    private static OAuth2LoginRequestPostProcessor member() {
        return oauth2Login().attributes(attributes -> {
            attributes.put(AuthenticatedUser.USER_ID_ATTRIBUTE, USER.toString());
            attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "VERXT");
        });
    }

    private static CommentView view(long positionMs) {
        return view(positionMs, null, false);
    }

    private static CommentView view(long positionMs, Long endPositionMs, boolean edited) {
        return new CommentView(
                UUID.randomUUID(),
                positionMs,
                endPositionMs,
                "kick embolando com o baixo",
                USER,
                "VERXT",
                Instant.parse("2026-10-21T22:00:00Z"),
                edited,
                false,
                null,
                null,
                null,
                List.of());
    }
}
