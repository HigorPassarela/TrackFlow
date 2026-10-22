package dev.higor.trackflow.api.shared.error;

/** The caller is a member of the project but their role does not allow the operation. */
public final class PermissionDeniedException extends DomainException {

    public PermissionDeniedException(String message) {
        this("insufficient-role", message);
    }

    public PermissionDeniedException(String code, String message) {
        super(code, message);
    }
}
