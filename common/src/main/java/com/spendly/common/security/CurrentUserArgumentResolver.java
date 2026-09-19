package com.spendly.common.security;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Resolves {@link CurrentUser}-annotated parameters from the JWT on the security context. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && CurrentUserPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public @Nullable Object resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {

        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = annotation == null || annotation.required();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // An anonymous request never carries a JwtAuthenticationToken, so the type check
        // alone covers it - testing for AnonymousAuthenticationToken here would be dead code.
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth) || !authentication.isAuthenticated()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
            }
            return null;
        }
        return from(jwtAuth.getToken());
    }

    /** Flattens a Keycloak access token into a principal. Also used outside MVC, e.g. by JPA auditing. */
    public static CurrentUserPrincipal from(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token has no subject");
        }
        return new CurrentUserPrincipal(
                toUuid(subject),
                subject,
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username"),
                realmRoles(jwt));
    }

    private static UUID toUuid(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            // Not every issuer uses UUID subjects; derive a stable one so callers get a usable id.
            return UUID.nameUUIDFromBytes(subject.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static Set<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
        if (realmAccess == null || !(realmAccess.get(ROLES) instanceof Collection<?> roles)) {
            return Set.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toUnmodifiableSet());
    }
}
