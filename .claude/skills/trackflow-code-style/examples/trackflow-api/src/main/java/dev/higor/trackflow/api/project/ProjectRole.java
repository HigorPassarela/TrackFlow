package dev.higor.trackflow.api.project;

/**
 * Role of a member inside one project.
 *
 * <p>Declared from least to most privileged: {@link #isAtLeast(ProjectRole)} relies on this order,
 * so new roles must be inserted in the right position.
 */
public enum ProjectRole {
    LISTENER,
    EDITOR,
    OWNER;

    public boolean isAtLeast(ProjectRole minimum) {
        return compareTo(minimum) >= 0;
    }
}
