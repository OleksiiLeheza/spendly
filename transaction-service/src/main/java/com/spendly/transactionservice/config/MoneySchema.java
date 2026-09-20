package com.spendly.transactionservice.config;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(name = "Money", description = "A monetary amount; the amount is a decimal string so no client parses it as a float")
record MoneySchema(
        @Schema(pattern = "^-?\\d+(\\.\\d+)?$", example = "10.50", requiredMode = RequiredMode.REQUIRED) String amount,
        @Schema(description = "ISO-4217 code", pattern = "^[A-Z]{3}$", example = "USD", requiredMode = RequiredMode.REQUIRED) String currency) {
}
