package dev.higor.trackflow.api.shared.error;

/**
 * A rule about the current state was violated: the project is archived, the version is not ready,
 * the invite expired. Mapped to 409 because retrying the same request will not help until the
 * state changes.
 */
public final class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
