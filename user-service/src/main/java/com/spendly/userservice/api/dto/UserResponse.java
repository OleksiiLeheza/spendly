package com.spendly.userservice.api.dto;

import com.spendly.userservice.domain.enums.Role;
import com.spendly.userservice.domain.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(name = "UserResponse", description = "A Spendly user profile")
public record UserResponse(
        @Schema(description = "Profile id (UUIDv7)", requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(example = "dev@spendly.local", requiredMode = RequiredMode.REQUIRED) String email,
        @Nullable String firstName,
        @Nullable String lastName,
        @Schema(description = "Display name, falling back to the full name then the email", requiredMode = RequiredMode.REQUIRED) String displayName,
        @Nullable String avatarUrl,
        @Schema(example = "en-US") @Nullable String locale,
        @Schema(example = "Europe/Kyiv") @Nullable String zoneId,
        @Schema(description = "ISO-4217 code", example = "USD", requiredMode = RequiredMode.REQUIRED) String defaultCurrency,
        @Schema(description = "IdP alias for social sign-ups", example = "google") @Nullable String identityProvider,
        @Schema(requiredMode = RequiredMode.REQUIRED) Role role,
        @Schema(requiredMode = RequiredMode.REQUIRED) UserStatus status,
        @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt) {
}
