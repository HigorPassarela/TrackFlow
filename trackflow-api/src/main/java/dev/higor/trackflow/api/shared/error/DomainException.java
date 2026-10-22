package dev.higor.trackflow.api.shared.error;

/**
 * Base for every business error that may reach the HTTP layer.
 *
 * <p>Sealed so that {@link GlobalExceptionHandler} can map each subtype to exactly one status code
 * with an exhaustive {@code switch}: adding a new subtype breaks the build until it is mapped.
 * Clients branch on {@link #code()}, never on the message.
 */
public abstract sealed class DomainException extends RuntimeException
        permits NotFoundException, PermissionDeniedException, BusinessRuleException, InvalidInputException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Stable, kebab-case identifier of the problem, e.g. {@code version-not-ready}. */
    public String code() {
        return code;
    }
}
