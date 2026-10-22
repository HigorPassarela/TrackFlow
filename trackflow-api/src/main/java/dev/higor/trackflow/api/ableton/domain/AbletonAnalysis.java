package dev.higor.trackflow.api.ableton.domain;

import dev.higor.trackflow.api.ableton.AbletonAnalyses.ProjectSnapshot;
import dev.higor.trackflow.api.ableton.AbletonAnalyses.Track;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * The analysis of one version's Ableton project. Identified by the version id: a version has at
 * most one project file, so it has at most one analysis.
 */
@Entity
@Table(name = "ableton_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class AbletonAnalysis {

    @Id
    private UUID versionId;

    @Version
    private long rowVersion;

    private UUID projectId;

    @Enumerated(EnumType.STRING)
    private AnalysisStatus status;

    private @Nullable String failureReason;

    private UUID jobId;

    private @Nullable String liveVersion;

    private boolean verifiedVersion;

    private @Nullable Double tempo;

    private @Nullable String timeSignature;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Track> tracks;

    private Instant createdAt;

    private @Nullable Instant analyzedAt;

    @Builder
    private AbletonAnalysis(UUID versionId, UUID projectId, UUID jobId, Instant createdAt) {
        this.versionId = versionId;
        this.projectId = projectId;
        this.jobId = jobId;
        this.status = AnalysisStatus.PENDING;
        this.tracks = List.of();
        this.createdAt = createdAt;
    }

    /** @return whether the result was applied; {@code false} for duplicated or stale results */
    public boolean complete(UUID resultJobId, ProjectSnapshot snapshot, Instant at) {
        if (!isPending(resultJobId)) {
            return false;
        }
        status = AnalysisStatus.DONE;
        liveVersion = snapshot.liveVersion();
        verifiedVersion = snapshot.verifiedVersion();
        tempo = snapshot.tempo();
        timeSignature = snapshot.timeSignature();
        tracks = snapshot.tracks();
        analyzedAt = at;
        return true;
    }

    /** @return whether the failure was applied; {@code false} for duplicated or stale results */
    public boolean fail(UUID resultJobId, String reason, Instant at) {
        if (!isPending(resultJobId)) {
            return false;
        }
        status = AnalysisStatus.FAILED;
        failureReason = reason;
        analyzedAt = at;
        return true;
    }

    public boolean isDone() {
        return status == AnalysisStatus.DONE;
    }

    public ProjectSnapshot snapshot() {
        return new ProjectSnapshot(liveVersion, verifiedVersion, tempo, timeSignature, tracks);
    }

    private boolean isPending(UUID resultJobId) {
        return status == AnalysisStatus.PENDING && Objects.equals(jobId, resultJobId);
    }
}
