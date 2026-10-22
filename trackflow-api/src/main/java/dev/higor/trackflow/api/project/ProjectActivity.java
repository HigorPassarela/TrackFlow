package dev.higor.trackflow.api.project;

import java.util.UUID;

/**
 * Lets other modules bump a project to the top of its members' lists. Called synchronously by
 * {@code version} and {@code comment}: an event would create a dependency cycle, because those
 * modules already depend on {@code project}.
 */
public interface ProjectActivity {

    void record(UUID projectId);
}
