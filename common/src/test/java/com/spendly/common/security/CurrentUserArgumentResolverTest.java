package com.spendly.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserArgumentResolverTest {

    private static final String SUBJECT = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    @Test
    void flattensTheKeycloakTokenIntoAPrincipal() {
        CurrentUserPrincipal principal = CurrentUserArgumentResolver.from(jwt(SUBJECT,
                Map.of(
                        "email", "oleksii@example.com",
                        "preferred_username", "oleksii",
                        "realm_access", Map.of("roles", List.of("USER", "ADMIN")))));

        assertThat(principal.id()).isEqualTo(UUID.fromString(SUBJECT));
        assertThat(principal.email()).isEqualTo("oleksii@example.com");
        assertThat(principal.username()).isEqualTo("oleksii");
        assertThat(principal.roles()).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(principal.hasRole("ADMIN")).isTrue();
        assertThat(principal.hasRole("SUPPORT")).isFalse();
    }

    @Test
    void toleratesATokenWithoutRolesOrProfileClaims() {
        CurrentUserPrincipal principal = CurrentUserArgumentResolver.from(jwt(SUBJECT, Map.of()));

        assertThat(principal.roles()).isEmpty();
        assertThat(principal.email()).isNull();
        assertThat(principal.username()).isNull();
        assertThat(principal.households()).isEmpty();
        assertThat(principal.authenticatedAt()).isNull();
    }

    @Test
    void derivesAStableIdWhenTheIssuerSubjectIsNotAUuid() {
        // Google and other IdPs issue numeric subjects; the id must still be usable and stable.
        UUID first = CurrentUserArgumentResolver.from(jwt("104829301923", Map.of())).id();
        UUID second = CurrentUserArgumentResolver.from(jwt("104829301923", Map.of())).id();

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(CurrentUserArgumentResolver.from(jwt("999", Map.of())).id());
    }

    @Test
    void readsHouseholdsAndAuthTimeWhenPresent() {
        UUID household = UUID.randomUUID();
        Instant authTime = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        CurrentUserPrincipal principal = CurrentUserArgumentResolver.from(jwt(SUBJECT,
                Map.of(
                        "households", List.of(household.toString(), "/" + UUID.randomUUID(), "not-a-uuid"),
                        "auth_time", authTime.getEpochSecond())));

        assertThat(principal.households()).hasSize(2).contains(household);
        assertThat(principal.isMemberOf(household)).isTrue();
        assertThat(principal.isMemberOf(UUID.randomUUID())).isFalse();
        assertThat(principal.isMemberOf(null)).isFalse();
        assertThat(principal.authenticatedAt()).isEqualTo(authTime);
    }

    @Test
    void ignoresNonStringEntriesInTheRolesClaim() {
        CurrentUserPrincipal principal = CurrentUserArgumentResolver.from(jwt(SUBJECT,
                Map.of("realm_access", Map.of("roles", List.of("USER", 42, true)))));

        assertThat(principal.roles()).containsExactly("USER");
    }

    private static Jwt jwt(String subject, Map<String, Object> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        extraClaims.forEach(builder::claim);
        return builder.build();
    }
}
