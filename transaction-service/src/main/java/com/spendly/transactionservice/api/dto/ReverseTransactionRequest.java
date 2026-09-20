package com.spendly.transactionservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(name = "ReverseTransactionRequest", description = "Optional context recorded on the reversing entry")
public record ReverseTransactionRequest(@Size(max = 4000) @Nullable String reason) {
}
