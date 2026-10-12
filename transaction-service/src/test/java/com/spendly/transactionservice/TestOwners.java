package com.spendly.transactionservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

final class TestOwners {

    private TestOwners() {
    }

    static JwtRequestPostProcessor asAdmin(UUID adminId) {
        return asOwner(adminId).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    static JwtRequestPostProcessor asOwner(UUID ownerId) {
        return asOwnerAuthenticatedAgo(ownerId, Duration.ZERO);
    }

    static JwtRequestPostProcessor asOwnerAuthenticatedAgo(UUID ownerId, Duration ago) {
        return jwt().jwt(token -> token.subject(ownerId.toString())
                .claim("email", ownerId + "@spendly.test")
                .claim("preferred_username", "owner-" + ownerId)
                .claim("auth_time", Instant.now().minus(ago).getEpochSecond()));
    }
}
