package dev.higor.trackflow.api.project.application;

import dev.higor.trackflow.api.project.ProjectRole;
import java.util.UUID;

public record AcceptedInvite(UUID projectId, ProjectRole role) {}
