package dev.higor.trackflow.api.version.web;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.version.application.ProjectEventHub;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events with the status changes of a project's versions. With virtual threads, a long
 * open connection does not hold a platform thread.
 */
@RestController
class ProjectEventsController {

    private final ProjectEventHub hub;

    ProjectEventsController(ProjectEventHub hub) {
        this.hub = hub;
    }

    @GetMapping(path = "/api/projects/{projectId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable UUID projectId, AuthenticatedUser user) {
        return hub.subscribe(projectId, user.id());
    }
}
