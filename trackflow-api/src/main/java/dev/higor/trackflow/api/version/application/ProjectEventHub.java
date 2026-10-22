package dev.higor.trackflow.api.version.application;

import dev.higor.trackflow.api.project.ProjectAccess;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.version.VersionStatusChanged;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Real-time status for the members connected to a project (design D9). Emitters live in memory, so
 * with several API instances each one only notifies its own clients; accepted for the MVP.
 */
@Component
public class ProjectEventHub {

    private static final Logger log = LoggerFactory.getLogger(ProjectEventHub.class);

    static final Duration TIMEOUT = Duration.ofMinutes(30);
    static final String STATUS_EVENT = "version-status";

    private final ProjectAccess access;
    private final Map<UUID, List<SseEmitter>> emittersByProject = new ConcurrentHashMap<>();

    public ProjectEventHub(ProjectAccess access) {
        this.access = access;
    }

    /** Non-members get the usual 404 before any connection is kept. */
    public SseEmitter subscribe(UUID projectId, UUID userId) {
        access.requireRole(projectId, userId, ProjectRole.LISTENER);
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        List<SseEmitter> emitters = emittersByProject.computeIfAbsent(projectId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        Runnable remove = () -> remove(projectId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        log.debug("Status channel opened projectId={} userId={}", projectId, userId);
        return emitter;
    }

    /** After commit only: a transition that rolled back is never announced. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(VersionStatusChanged event) {
        StatusMessage message = new StatusMessage(
                event.versionId(), event.status().name().toLowerCase(Locale.ROOT), event.occurredAt());
        for (SseEmitter emitter : emittersByProject.getOrDefault(event.projectId(), List.of())) {
            send(
                    event.projectId(),
                    emitter,
                    SseEmitter.event().name(STATUS_EVENT).data(message));
        }
    }

    /** Keeps proxies from closing idle connections. */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        emittersByProject.forEach((projectId, emitters) -> emitters.forEach(
                emitter -> send(projectId, emitter, SseEmitter.event().comment("keepalive"))));
    }

    int subscribers(UUID projectId) {
        return emittersByProject.getOrDefault(projectId, List.of()).size();
    }

    private void send(UUID projectId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // The client went away; the browser reconnects on its own if it still wants updates.
            remove(projectId, emitter);
        }
    }

    private void remove(UUID projectId, SseEmitter emitter) {
        emittersByProject.computeIfPresent(projectId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    /** JSON sent to the browser for each status change. */
    record StatusMessage(UUID versionId, String status, Instant occurredAt) {}
}
