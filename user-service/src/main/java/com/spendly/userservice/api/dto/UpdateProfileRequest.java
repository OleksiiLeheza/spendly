package com.spendly.userservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(name = "UpdateProfileRequest", description = "Partial update; omitted fields are left unchanged")
public record UpdateProfileRequest(
        @Size(max = 100) @Nullable String firstName,
        @Size(max = 100) @Nullable String lastName,
        @Size(max = 200) @Nullable String displayName,
        @Size(max = 35) @Nullable String locale,
        @Size(max = 64) @Nullable String zoneId,
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a three-letter ISO-4217 code") @Schema(example = "EUR") @Nullable String defaultCurrency) {
}
