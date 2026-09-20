package com.spendly.userservice.service;

import com.spendly.common.web.ApiException;
import com.spendly.userservice.domain.User;
import com.spendly.userservice.repository.UserRepository;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
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
