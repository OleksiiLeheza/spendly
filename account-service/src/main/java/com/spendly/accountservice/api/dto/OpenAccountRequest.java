package com.spendly.accountservice.api.dto;

import com.spendly.accountservice.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "OpenAccountRequest", description = "Opens a new account with a zero balance in the given currency")
public record OpenAccountRequest(
        @NotBlank @Size(max = 100) @Schema(example = "Main checking", requiredMode = RequiredMode.REQUIRED) String name,
        @NotNull @Schema(requiredMode = RequiredMode.REQUIRED) AccountType type,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a three-letter ISO-4217 code") @Schema(description = "ISO-4217 code", example = "EUR", requiredMode = RequiredMode.REQUIRED) String currency) {
}
