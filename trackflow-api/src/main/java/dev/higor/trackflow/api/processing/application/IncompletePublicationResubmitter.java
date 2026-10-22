package dev.higor.trackflow.api.processing.application;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The retry half of the outbox (design D6): events whose listener failed, for example because the
 * broker was down while sending a job, are handed to their listeners again. The age threshold keeps
 * this from racing with listeners that are still running.
 */
@Component
class IncompletePublicationResubmitter {

    private static final Logger log = LoggerFactory.getLogger(IncompletePublicationResubmitter.class);

    static final Duration MINIMUM_AGE = Duration.ofMinutes(2);

    private final IncompleteEventPublications publications;

    IncompletePublicationResubmitter(IncompleteEventPublications publications) {
        this.publications = publications;
    }

    @Scheduled(
            fixedDelayString = "${trackflow.processing.resubmit-interval:5m}",
            initialDelayString = "${trackflow.processing.resubmit-interval:5m}")
    void resubmit() {
        log.debug("Resubmitting incomplete event publications olderThan={}", MINIMUM_AGE);
        publications.resubmitIncompletePublicationsOlderThan(MINIMUM_AGE);
    }
}
