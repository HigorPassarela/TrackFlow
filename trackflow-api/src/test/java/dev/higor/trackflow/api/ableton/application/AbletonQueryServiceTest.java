package dev.higor.trackflow.api.ableton.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import dev.higor.trackflow.api.ableton.AbletonAnalyses.ProjectSnapshot;
import dev.higor.trackflow.api.ableton.AbletonAnalyses.Track;
import dev.higor.trackflow.api.ableton.domain.AbletonAnalysis;
import dev.higor.trackflow.api.ableton.domain.AbletonAnalysisRepository;
import dev.higor.trackflow.api.ableton.domain.AbletonDiff;
import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbletonQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T20:00:00Z");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID V3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID V4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID LISTENER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Mock
    private AbletonAnalysisRepository analyses;

    @Mock
    private ProjectAccess access;

    private AbletonQueryService service;

    @BeforeEach
    void setUp() {
        service = new AbletonQueryService(analyses, access);
    }

    @Test
    @DisplayName("Scenario: member reads the summary")
    void listenerReadsTheSummary() {
        given(analyses.findByVersionIdAndProjectId(V4, PROJECT)).willReturn(Optional.of(done(V4, 126.0)));

        AnalysisView view = service.find(PROJECT, V4, LISTENER);

        then(access).should().requireRole(PROJECT, LISTENER, ProjectRole.LISTENER);
        assertThat(view.project()).isNotNull();
        assertThat(view.project().tempo()).isEqualTo(126.0);
    }

    @Test
    void comparesTheVersionInTheRequestAgainstTheOtherOne() {
        given(analyses.findByVersionIdAndProjectId(V3, PROJECT)).willReturn(Optional.of(done(V3, 125.0)));
        given(analyses.findByVersionIdAndProjectId(V4, PROJECT)).willReturn(Optional.of(done(V4, 126.0)));

        AbletonDiff diff = service.diff(PROJECT, V4, V3, LISTENER);

        assertThat(diff.tempo()).isEqualTo(new AbletonDiff.TempoChange(125.0, 126.0));
    }

    @Test
    @DisplayName("Scenario: version without an analyzed project")
    void refusesDiffNamingTheVersionWithoutAnalysis() {
        given(analyses.findByVersionIdAndProjectId(V3, PROJECT)).willReturn(Optional.of(done(V3, 125.0)));
        given(analyses.findByVersionIdAndProjectId(V4, PROJECT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.diff(PROJECT, V4, V3, LISTENER))
                .isInstanceOfSatisfying(NotFoundException.class, error -> {
                    assertThat(error.code()).isEqualTo("ableton-analysis-not-found");
                    assertThat(error.getMessage()).contains(V4.toString());
                });
    }

    @Test
    void refusesDiffWhileAnAnalysisIsStillPending() {
        AbletonAnalysis pending = AbletonAnalysis.builder()
                .versionId(V3)
                .projectId(PROJECT)
                .jobId(JOB)
                .createdAt(NOW)
                .build();
        given(analyses.findByVersionIdAndProjectId(V3, PROJECT)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.diff(PROJECT, V4, V3, LISTENER))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.code()).isEqualTo("ableton-analysis-unavailable");
                    assertThat(error.getMessage()).contains(V3.toString(), "pending");
                });
    }

    @Test
    void nonMemberIsRejectedBeforeAnythingIsRead() {
        given(access.requireRole(PROJECT, LISTENER, ProjectRole.LISTENER))
                .willThrow(NotFoundException.of("project", PROJECT));

        assertThatThrownBy(() -> service.find(PROJECT, V4, LISTENER)).isInstanceOf(NotFoundException.class);
        then(analyses).shouldHaveNoInteractions();
    }

    private static AbletonAnalysis done(UUID versionId, double tempo) {
        AbletonAnalysis analysis = AbletonAnalysis.builder()
                .versionId(versionId)
                .projectId(PROJECT)
                .jobId(JOB)
                .createdAt(NOW)
                .build();
        analysis.complete(
                JOB,
                new ProjectSnapshot(
                        "Ableton Live 12.4.6",
                        true,
                        tempo,
                        "4/4",
                        List.of(new Track("8", "audio", "Kick", List.of(), List.of()))),
                NOW);
        return analysis;
    }
}
