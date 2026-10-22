package dev.higor.trackflow.api.shared.error;

/**
 * The request is invalid by a rule that Bean Validation cannot express because it depends on
 * configuration, such as the maximum upload size. Mapped to 400, like Bean Validation errors.
 */
public final class InvalidInputException extends DomainException {

    public InvalidInputException(String code, String message) {
        super(code, message);
    }
}
