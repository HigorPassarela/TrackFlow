package dev.higor.trackflow.api.shared.error;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The only place that turns exceptions into HTTP responses. Every error leaves the API as an RFC
 * 9457 problem with an extra {@code code} property.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final URI ERROR_TYPE_BASE = URI.create("https://trackflow.dev/errors/");

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomainException(DomainException exception) {
        HttpStatus status = switch (exception) {
            case NotFoundException notFound -> HttpStatus.NOT_FOUND;
            case PermissionDeniedException denied -> HttpStatus.FORBIDDEN;
            case BusinessRuleException ruleViolation -> HttpStatus.CONFLICT;
            case InvalidInputException invalidInput -> HttpStatus.BAD_REQUEST;
        };
        return problem(status, exception.code(), exception.getMessage());
    }

    /** Two requests changed the same row at once (for example, two people accepting one invite). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "concurrent-update", "Someone else changed this resource; try again");
    }

    /** Reached through the security entry point for {@code /api/**}: JSON instead of a login redirect. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleUnauthenticated(AuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to use the API");
    }

    /** Reached through the security access denied handler, in practice for a missing CSRF token. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        String code = exception instanceof CsrfException ? "invalid-csrf-token" : "access-denied";
        return problem(HttpStatus.FORBIDDEN, code, "The request was refused");
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "The request has invalid fields");
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", violations);
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Constraints on path and query parameters, such as {@code size} above the page limit. */
    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "The request has invalid fields");
        List<FieldViolation> violations = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", violations);
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Every other framework error (malformed JSON, wrong method, unknown route) gets the same shape:
     * a {@code code} derived from the status, so clients never need to special-case them.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            String code = HttpStatus.resolve(statusCode.value()) instanceof HttpStatus status
                    ? status.name().toLowerCase(Locale.ROOT).replace('_', '-')
                    : "error";
            problem.setType(ERROR_TYPE_BASE.resolve(code));
            problem.setProperty("code", code);
        }
        return super.handleExceptionInternal(exception, body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatus status, String code, @Nullable String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(ERROR_TYPE_BASE.resolve(code));
        problem.setProperty("code", code);
        return problem;
    }

    record FieldViolation(String field, @Nullable String message) {}
}
