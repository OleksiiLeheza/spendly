package com.spendly.accountservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

final class TestOwners {

    private TestOwners() {
    }

    static JwtRequestPostProcessor asOwner(UUID ownerId) {
        return jwt().jwt(token -> token.subject(ownerId.toString())
                .claim("email", ownerId + "@spendly.test")
                .claim("preferred_username", "owner-" + ownerId));
    }

    static JwtRequestPostProcessor asAdmin(UUID adminId) {
        return asOwner(adminId).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    static void actAs(UUID ownerId) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(ownerId.toString())
                .claim("email", ownerId + "@spendly.test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        TestSecurityContextHolder.setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
    }

    static void actAsNobody() {
        TestSecurityContextHolder.clearContext();
    }

    static JwtRequestPostProcessor asMemberOf(UUID userId, UUID... households) {
        return jwt().jwt(token -> token.subject(userId.toString())
                .claim("email", userId + "@spendly.test")
                .claim("preferred_username", "member-" + userId)
                .claim("households", Arrays.stream(households).map(UUID::toString).toList()));
    }
}
