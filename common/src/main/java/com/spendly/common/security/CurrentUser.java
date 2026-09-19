package com.spendly.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated {@link CurrentUserPrincipal} into a controller method parameter.
 *
 * <pre>{@code
 * &#64;GetMapping("/me")
 * UserResponse me(@CurrentUser CurrentUserPrincipal principal) { ... }
 * }</pre>
 *
 * <p>
 * Resolved by {@link CurrentUserArgumentResolver}. Mark the parameter {@code @CurrentUser(required = false)} to receive
 * {@code null} on anonymous requests.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    /** When true (the default), an unauthenticated request fails instead of injecting null. */
    boolean required() default true;
}
