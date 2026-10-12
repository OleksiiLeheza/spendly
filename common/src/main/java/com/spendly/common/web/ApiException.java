package com.spendly.common.web;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
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

    public HttpHeaders headers() {
        return new HttpHeaders();
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

    public static class StepUpRequired extends ApiException {

        private final Duration maxAge;

        public StepUpRequired(Duration maxAge) {
            super(
                    HttpStatus.UNAUTHORIZED,
                    URI.create("https://spendly.dev/problems/step-up-required"),
                    "Recent authentication required",
                    "Moving money requires an authentication no older than " + maxAge.toMinutes()
                            + " minutes; sign in again and retry");
            this.maxAge = maxAge;
        }

        public Duration maxAge() {
            return maxAge;
        }

        @Override
        public HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.WWW_AUTHENTICATE,
                    "Bearer error=\"insufficient_user_authentication\", error_description=\"A recent authentication is required\", max_age=\""
                            + maxAge.toSeconds() + "\"");
            return headers;
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

    /** A dependency this request needs, such as the broker, did not answer in time. */
    public static class Unavailable extends ApiException {
        public Unavailable(String detail) {
            super(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    URI.create("https://spendly.dev/problems/unavailable"),
                    "Dependency unavailable",
                    detail);
        }
    }
}
