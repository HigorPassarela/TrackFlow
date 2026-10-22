package dev.higor.trackflow.api.project.domain;

import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A user's role in one project. Every project has exactly one {@link ProjectRole#OWNER} row. */
@Entity
@Table(name = "project_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class ProjectMember {

    @EmbeddedId
    private ProjectMemberId id;

    @Enumerated(EnumType.STRING)
    private ProjectRole role;

    private Instant joinedAt;

    @Builder
    private ProjectMember(UUID projectId, UUID userId, ProjectRole role, Instant joinedAt) {
        this.id = new ProjectMemberId(projectId, userId);
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID projectId() {
        return id.projectId();
    }

    public UUID userId() {
        return id.userId();
    }

    public boolean isOwner() {
        return role == ProjectRole.OWNER;
    }

    /** Ownership is not transferable in the MVP: the owner keeps the role and nobody else gets it. */
    public void changeRole(ProjectRole newRole) {
        if (isOwner()) {
            throw new BusinessRuleException("owner-role-cannot-change", "The project needs an owner");
        }
        if (newRole == ProjectRole.OWNER) {
            throw new BusinessRuleException("single-owner", "A project has exactly one owner");
        }
        role = newRole;
    }

    /** Called before removing a member or letting them leave. */
    public void requireRemovable() {
        if (isOwner()) {
            throw new BusinessRuleException("owner-cannot-leave", "The project needs an owner");
        }
    }
}
