package dev.higor.trackflow.api.project.domain;

import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public record ProjectMemberId(UUID projectId, UUID userId) {}
