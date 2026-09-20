package com.spendly.transactionservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Schema(name = "TransactionFeedResponse", description = "One page of the feed, newest first")
public record TransactionFeedResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) List<TransactionFeedItem> items,
        @Schema(description = "Pass as ?cursor= to fetch the next page; absent on the last page") @Nullable String nextCursor) {

    public TransactionFeedResponse {
        items = List.copyOf(items);
    }
}
