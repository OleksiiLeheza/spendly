package com.spendly.accountservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

final class TestOwners {

    private TestOwners() {
    }

    static JwtRequestPostProcessor asOwner(UUID ownerId) {
        return jwt().jwt(token -> token.subject(ownerId.toString())
                .claim("email", ownerId + "@spendly.test")
                .claim("preferred_username", "owner-" + ownerId));
    }
}
