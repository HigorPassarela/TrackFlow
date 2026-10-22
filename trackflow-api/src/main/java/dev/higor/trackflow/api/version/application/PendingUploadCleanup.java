package dev.higor.trackflow.api.version.application;

import dev.higor.trackflow.api.storage.ObjectStorage;
import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.domain.TrackVersion;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Discards uploads that were never confirmed, together with whatever reached the storage. Each
 * version is removed in its own short transaction; storage calls happen after the commit.
 */
@Component
class PendingUploadCleanup {

    private static final Logger log = LoggerFactory.getLogger(PendingUploadCleanup.class);

    private final TrackVersionRepository versions;
    private final ObjectStorage storage;
    private final TransactionTemplate transactions;
    private final VersionProperties properties;
    private final Clock clock;

    PendingUploadCleanup(
            TrackVersionRepository versions,
            ObjectStorage storage,
            TransactionTemplate transactions,
            VersionProperties properties,
            Clock clock) {
        this.versions = versions;
        this.storage = storage;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *")
    void discardAbandonedUploads() {
        Instant cutoff = clock.instant().minus(properties.pendingCleanup().maxAge());
        int discarded = 0;
        for (TrackVersion version : versions.findByStatusAndCreatedAtBefore(VersionStatus.PENDING_UPLOAD, cutoff)) {
            Integer deleted = transactions.execute(status -> versions.deleteIfStillPending(version.getId()));
            if (deleted == null || deleted == 0) {
                continue;
            }
            deleteObjects(version);
            discarded++;
        }
        if (discarded > 0) {
            log.info("Abandoned uploads discarded count={} cutoff={}", discarded, cutoff);
        }
    }

    /** The row is gone already; a failure here only leaves an orphan object behind, so log and go on. */
    private void deleteObjects(TrackVersion version) {
        try {
            storage.delete(version.getSourceKey());
            if (version.getAlsKey() != null) {
                storage.delete(version.getAlsKey());
            }
        } catch (RuntimeException e) {
            log.warn("Could not delete objects of discarded upload versionId={}", version.getId(), e);
        }
    }
}
