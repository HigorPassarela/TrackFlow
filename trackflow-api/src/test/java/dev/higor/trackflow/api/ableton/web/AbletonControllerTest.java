package dev.higor.trackflow.api.ableton.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.ableton.AbletonAnalyses.ProjectSnapshot;
import dev.higor.trackflow.api.ableton.AbletonAnalyses.Track;
import dev.higor.trackflow.api.ableton.application.AbletonQueryService;
import dev.higor.trackflow.api.ableton.application.AnalysisView;
import dev.higor.trackflow.api.ableton.domain.AbletonDiff;
import dev.higor.trackflow.api.ableton.domain.AnalysisStatus;
import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AbletonController.class)
class AbletonControllerTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PROJECT_VERSION = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID V3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID V4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID LISTENER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String ABLETON = "/api/projects/{projectId}/versions/{versionId}/ableton";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AbletonQueryService service;

    @Test
    @DisplayName("Scenario: member reads the summary")
    void listenerGetsTheSummary() {
        given(service.find(PROJECT, V4, LISTENER)).willReturn(doneView());

        var response = assertThat(mvc.get().uri(ABLETON, PROJECT, V4).with(listener()))
                .hasStatusOk()
                .bodyJson();

        response.extractingPath("$.status").isEqualTo("done");
        response.extractingPath("$.project.tempo").isEqualTo(126.0);
        response.extractingPath("$.project.tracks[1].plugins[0]").isEqualTo("Serum");
    }

    @Test
    void versionWithoutAnalysisIsNotFound() {
        given(service.find(PROJECT, V3, LISTENER))
                .willThrow(new NotFoundException("ableton-analysis-not-found", "Version " + V3 + " has no analysis"));

        assertThat(mvc.get().uri(ABLETON, PROJECT, V3).with(listener()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("ableton-analysis-not-found");
    }

    @Test
    void diffComparesTheVersionAgainstTheOtherOne() {
        given(service.diff(PROJECT, V4, V3, LISTENER))
                .willReturn(new AbletonDiff(
                        new AbletonDiff.TempoChange(125.0, 126.0), List.of(), List.of(), List.of(), List.of()));

        var response = assertThat(mvc.get()
                        .uri(ABLETON + "/diff", PROJECT, V4)
                        .param("against", V3.toString())
                        .with(listener()))
                .hasStatusOk()
                .bodyJson();

        response.extractingPath("$.fromVersionId").isEqualTo(V3.toString());
        response.extractingPath("$.changes.tempo.to").isEqualTo(126.0);
    }

    @Test
    @DisplayName("Scenario: versions of different projects")
    void versionOfAnotherProjectIsNotFound() {
        given(service.diff(PROJECT, V4, OTHER_PROJECT_VERSION, LISTENER))
                .willThrow(new NotFoundException(
                        "ableton-analysis-not-found", "Version " + OTHER_PROJECT_VERSION + " has no analysis"));

        assertThat(mvc.get()
                        .uri(ABLETON + "/diff", PROJECT, V4)
                        .param("against", OTHER_PROJECT_VERSION.toString())
                        .with(listener()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void pendingAnalysisCannotBeCompared() {
        given(service.diff(PROJECT, V4, V3, LISTENER))
                .willThrow(new BusinessRuleException("ableton-analysis-unavailable", "pending"));

        assertThat(mvc.get()
                        .uri(ABLETON + "/diff", PROJECT, V4)
                        .param("against", V3.toString())
                        .with(listener()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("ableton-analysis-unavailable");
    }

    @Test
    void nonMemberGetsNotFound() {
        given(service.find(PROJECT, V4, LISTENER)).willThrow(NotFoundException.of("project", PROJECT));

        assertThat(mvc.get().uri(ABLETON, PROJECT, V4).with(listener()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("project-not-found");
    }

    @Test
    void diffRequiresTheOtherVersion() {
        assertThat(mvc.get().uri(ABLETON + "/diff", PROJECT, V4).with(listener()))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private static OAuth2LoginRequestPostProcessor listener() {
        return oauth2Login().attributes(attributes -> {
            attributes.put(AuthenticatedUser.USER_ID_ATTRIBUTE, LISTENER.toString());
            attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "Ouvinte");
        });
    }

    private static AnalysisView doneView() {
        return new AnalysisView(
                V4,
                AnalysisStatus.DONE,
                null,
                new ProjectSnapshot(
                        "Ableton Live 12.4.6",
                        true,
                        126.0,
                        "4/4",
                        List.of(
                                new Track("8", "audio", "Kick", List.of("Compressor2"), List.of()),
                                new Track("12", "midi", "Bass", List.of("PluginDevice"), List.of("Serum")))),
                Instant.parse("2026-10-22T20:00:00Z"));
    }
}
