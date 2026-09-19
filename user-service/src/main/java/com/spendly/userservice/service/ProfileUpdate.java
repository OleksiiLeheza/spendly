package com.spendly.userservice.service;

import org.jspecify.annotations.Nullable;

public record ProfileUpdate(
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String displayName,
        @Nullable String locale,
        @Nullable String zoneId,
        @Nullable String defaultCurrency) {
}
