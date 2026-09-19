package com.spendly.common.web;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Base class for errors that map straight onto an RFC 9457 problem response.
 *
 * <p>
 * Services throw these instead of assembling a {@code ProblemDetail} in a controller; {@link GlobalExceptionHandler}
 * does the conversion in one place.
 */
public class ApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final URI type;
    private final String title;

    protected ApiException(HttpStatusCode status, URI type, String title, String detail) {
        this(status, type, title, detail, null);
    }

    protected ApiException(
            HttpStatusCode status, URI type, String title, String detail, @Nullable Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    public HttpStatusCode status() {
        return status;
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }

    /** The request was understood but names something that does not exist. */
    public static class NotFound extends ApiException {
        public NotFound(String resource, Object id) {
            super(
                    HttpStatus.NOT_FOUND,
                    URI.create("https://spendly.dev/problems/not-found"),
                    "Resource not found",
                    "%s %s does not exist".formatted(resource, id));
        }
    }

    /** The request conflicts with the current state, e.g. a duplicate unique key. */
    public static class Conflict extends ApiException {
        public Conflict(String detail) {
            super(
                    HttpStatus.CONFLICT,
                    URI.create("https://spendly.dev/problems/conflict"),
                    "Conflict",
                    detail);
        }
    }

    /** The caller is authenticated but not allowed to touch this resource. */
    public static class Forbidden extends ApiException {
        public Forbidden(String detail) {
            super(
                    HttpStatus.FORBIDDEN,
                    URI.create("https://spendly.dev/problems/forbidden"),
                    "Forbidden",
                    detail);
        }
    }

    /** The request is well-formed but breaks a domain rule. */
    public static class Unprocessable extends ApiException {
        public Unprocessable(String detail) {
            super(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    URI.create("https://spendly.dev/problems/unprocessable"),
                    "Unprocessable request",
                    detail);
        }
    }
}
