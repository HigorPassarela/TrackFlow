package dev.higor.trackflow.api.ableton.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.higor.trackflow.api.ableton.AbletonAnalyses.ProjectSnapshot;
import dev.higor.trackflow.api.ableton.AbletonAnalyses.Track;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AbletonAnalysisTest {

    private static final Instant NOW = Instant.parse("2026-10-22T20:00:00Z");
    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_JOB = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final ProjectSnapshot SNAPSHOT = new ProjectSnapshot(
            "Ableton Live 12.4.6", true, 126.0, "4/4", List.of(new Track("8", "audio", "Kick", List.of(), List.of())));

    @Test
    void completesWithTheResultOfItsOwnJob() {
        AbletonAnalysis analysis = pending();

        boolean applied = analysis.complete(JOB, SNAPSHOT, NOW);

        assertThat(applied).isTrue();
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(analysis.snapshot()).isEqualTo(SNAPSHOT);
    }

    @Test
    void ignoresResultOfAnotherJob() {
        AbletonAnalysis analysis = pending();

        assertThat(analysis.fail(OTHER_JOB, "invalid-project-file", NOW)).isFalse();
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PENDING);
    }

    @Test
    void ignoresDuplicatedResultAfterFinishing() {
        AbletonAnalysis analysis = pending();
        analysis.fail(JOB, "invalid-project-file", NOW);

        boolean applied = analysis.complete(JOB, SNAPSHOT, NOW.plusSeconds(1));

        assertThat(applied).isFalse();
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getFailureReason()).isEqualTo("invalid-project-file");
    }

    @Test
    void pendingAnalysisFollowsTheJobThatTookOver() {
        AbletonAnalysis analysis = pending();

        analysis.reassignPendingJob(OTHER_JOB);

        assertThat(analysis.complete(JOB, SNAPSHOT, NOW)).isFalse();
        assertThat(analysis.complete(OTHER_JOB, SNAPSHOT, NOW)).isTrue();
    }

    @Test
    void finishedAnalysisKeepsItsJob() {
        AbletonAnalysis analysis = pending();
        analysis.complete(JOB, SNAPSHOT, NOW);

        analysis.reassignPendingJob(OTHER_JOB);

        assertThat(analysis.getJobId()).isEqualTo(JOB);
    }

    private static AbletonAnalysis pending() {
        return AbletonAnalysis.builder()
                .versionId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .jobId(JOB)
                .createdAt(NOW)
                .build();
    }
}
