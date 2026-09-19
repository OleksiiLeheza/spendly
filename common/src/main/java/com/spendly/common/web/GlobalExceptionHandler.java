package com.spendly.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into an RFC 9457 {@link ProblemDetail}, so clients see one error shape.
 *
 * <p>
 * Each response carries the request's correlation id, which is what lets a user paste an error into a ticket and have
 * someone find the matching log line.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI VALIDATION_TYPE = URI.create("https://spendly.dev/problems/validation");
    private static final URI INTERNAL_TYPE = URI.create("https://spendly.dev/problems/internal");

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setType(ex.type());
        problem.setTitle(ex.title());
        decorate(problem, request);

        if (ex.status().is5xxServerError()) {
            log.error("Request failed: {}", ex.getMessage(), ex);
        } else {
            log.debug("Request rejected: {}", ex.getMessage());
        }
        return ResponseEntity.status(ex.status()).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You may not access this resource");
        problem.setTitle("Forbidden");
        decorate(problem, request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required");
        problem.setTitle("Unauthorized");
        decorate(problem, request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad request");
        decorate(problem, request);
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Last resort. The thrown message may name internal details, so the client gets a fixed string and the correlation
     * id; the real cause goes to the log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side");
        problem.setType(INTERNAL_TYPE);
        problem.setTitle("Internal server error");
        decorate(problem, request);
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError().body(problem);
    }

    /** Field-level bean-validation failures, reported as an {@code errors} map. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.merge(
                        error.getField(),
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage(),
                        (first, second) -> first + "; " + second));
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(
                        error.getObjectName(),
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(VALIDATION_TYPE);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        decorate(problem, servletRequestOf(request));

        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ProblemDetail problem = body instanceof ProblemDetail existing
                ? existing
                : ProblemDetail.forStatusAndDetail(statusCode, messageOf(ex, statusCode));
        decorate(problem, servletRequestOf(request));
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private static String messageOf(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }
        return HttpStatus.valueOf(statusCode.value()).getReasonPhrase();
    }

    private static void decorate(ProblemDetail problem, @Nullable HttpServletRequest request) {
        problem.setProperty("timestamp", Instant.now().toString());
        if (request == null) {
            return;
        }
        problem.setInstance(URI.create(request.getRequestURI()));
        Object correlationId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }

    private static @Nullable HttpServletRequest servletRequestOf(WebRequest request) {
        return request instanceof ServletWebRequest servletWebRequest
                ? servletWebRequest.getRequest()
                : null;
    }

    /** Kept for subclasses that want to add fields without rebuilding the map. */
    protected static Map<String, Object> mutableProperties(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        return properties == null ? new LinkedHashMap<>() : properties;
    }
}
