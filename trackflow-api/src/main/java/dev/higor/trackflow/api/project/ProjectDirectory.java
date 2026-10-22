package dev.higor.trackflow.api.project;

import java.util.Optional;
import java.util.UUID;

/**
 * Public project data for places that have no member to check, such as the public listening page.
 * Callers are responsible for having authorized the access some other way (a valid share link).
 */
public interface ProjectDirectory {

    Optional<String> findTitle(UUID projectId);
}
