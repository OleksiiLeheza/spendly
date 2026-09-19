package com.spendly.userservice.service;

import com.spendly.common.security.CurrentUserPrincipal;
import com.spendly.common.web.ApiException;
import com.spendly.userservice.domain.User;
import com.spendly.userservice.domain.enums.Role;
import com.spendly.userservice.repository.UserRepository;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public User provision(CurrentUserPrincipal principal) {
        return users.findActiveByKeycloakId(principal.id())
                .map(existing -> refresh(existing, principal))
                .orElseGet(() -> create(principal));
    }

    @PreAuthorize("isAuthenticated() and #keycloakId.toString() == authentication.token.subject")
    public User getByKeycloakId(UUID keycloakId) {
        return users.findActiveByKeycloakId(keycloakId)
                .orElseThrow(() -> new ApiException.NotFound("User", keycloakId));
    }

    @Transactional
    @PreAuthorize("isAuthenticated() and #keycloakId.toString() == authentication.token.subject")
    public User updateProfile(UUID keycloakId, ProfileUpdate update) {
        User user = users.findActiveByKeycloakId(keycloakId)
                .orElseThrow(() -> new ApiException.NotFound("User", keycloakId));

        if (update.firstName() != null) {
            user.setFirstName(blankToNull(update.firstName()));
        }
        if (update.lastName() != null) {
            user.setLastName(blankToNull(update.lastName()));
        }
        if (update.displayName() != null) {
            user.setDisplayName(blankToNull(update.displayName()));
        }
        if (update.locale() != null) {
            user.setLocale(blankToNull(update.locale()));
        }
        if (update.zoneId() != null) {
            user.setZoneId(blankToNull(update.zoneId()));
        }
        if (update.defaultCurrency() != null) {
            user.setDefaultCurrency(validCurrency(update.defaultCurrency()));
        }
        return user;
    }

    private User create(CurrentUserPrincipal principal) {
        User user = new User();
        user.setKeycloakId(principal.id());
        user.setEmail(requireEmail(principal));
        user.setDisplayName(principal.username());
        user.setRole(principal.hasRole("ADMIN") ? Role.ADMIN : Role.USER);

        try {
            User saved = users.saveAndFlush(user);
            log.info("Provisioned local profile for subject {}", principal.subject());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            log.debug("Concurrent provisioning for subject {}, re-reading", principal.subject());
            return users.findActiveByKeycloakId(principal.id())
                    .orElseThrow(() -> new ApiException.Conflict("Could not provision a profile for this identity"));
        }
    }

    private User refresh(User user, CurrentUserPrincipal principal) {
        String email = requireEmail(principal);
        if (!email.equalsIgnoreCase(user.getEmail())) {
            user.setEmail(email);
        }
        Role tokenRole = principal.hasRole("ADMIN") ? Role.ADMIN : Role.USER;
        if (user.getRole() != tokenRole) {
            user.setRole(tokenRole);
        }
        return user;
    }

    private static String requireEmail(CurrentUserPrincipal principal) {
        String email = principal.email();
        if (email == null || email.isBlank()) {
            throw new ApiException.Unprocessable("The access token carries no email claim; request the 'email' scope");
        }
        return email;
    }

    private static String validCurrency(String code) {
        try {
            return Currency.getInstance(code.toUpperCase(Locale.ROOT)).getCurrencyCode();
        } catch (IllegalArgumentException ex) {
            throw new ApiException.Unprocessable("'" + code + "' is not a known ISO-4217 currency");
        }
    }

    private static @Nullable String blankToNull(String value) {
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
