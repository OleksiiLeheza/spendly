package com.spendly.transactionservice.api;

import com.spendly.common.security.CurrentUser;
import com.spendly.common.security.CurrentUserPrincipal;
import com.spendly.transactionservice.api.dto.PostTransactionRequest;
import com.spendly.transactionservice.api.dto.ReverseTransactionRequest;
import com.spendly.transactionservice.api.dto.TransactionFeedResponse;
import com.spendly.transactionservice.api.dto.TransactionResponse;
import com.spendly.transactionservice.api.dto.UpdateTransactionRequest;
import com.spendly.transactionservice.api.mapper.TransactionMapper;
import com.spendly.transactionservice.domain.Transaction;
import com.spendly.transactionservice.domain.TransactionType;
import com.spendly.transactionservice.domain.FeedCursor;
import com.spendly.transactionservice.domain.FeedPage;
import com.spendly.transactionservice.domain.FeedQuery;
import com.spendly.transactionservice.service.PostedTransaction;
import com.spendly.transactionservice.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "The caller's ledger. Entries are appended, never edited or deleted; a correction is a reversal.")
@SecurityRequirement(name = "bearer-jwt")
public class TransactionController {

    private final TransactionService transactions;
    private final TransactionMapper mapper;

    public TransactionController(TransactionService transactions, TransactionMapper mapper) {
        this.transactions = transactions;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Post a ledger entry", description = "Send an Idempotency-Key header to make a retry return the entry created the first time.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The new entry"),
            @ApiResponse(responseCode = "200", description = "The entry an earlier request with the same Idempotency-Key created"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = {}),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "404", description = "A named category does not exist for this caller", content = {}),
            @ApiResponse(responseCode = "422", description = "A domain rule was broken", content = {})
    })
    public ResponseEntity<TransactionResponse> post(
            @CurrentUser CurrentUserPrincipal principal,
            @Parameter(description = "Client-chosen key that makes this request safe to retry") @RequestHeader(name = "Idempotency-Key", required = false) @Size(min = 8, max = 64) @Nullable String idempotencyKey,
            @Valid @RequestBody PostTransactionRequest request) {

        PostedTransaction posted = transactions.post(principal.id(), mapper.toCommand(request), idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(posted.transaction().getId())
                .toUri();
        return ResponseEntity.status(posted.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .location(location)
                .body(mapper.toResponse(posted.transaction()));
    }

    @GetMapping
    @Operation(summary = "The feed, newest first", description = "Keyset-paginated: pass nextCursor back as ?cursor= for the following page. Offsets are not supported.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of entries"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "422", description = "The cursor was not issued by this feed", content = {})
    })
    public TransactionFeedResponse feed(
            @CurrentUser CurrentUserPrincipal principal,
            @Parameter(description = "Only entries on, from or to this account") @RequestParam @Nullable UUID accountId,
            @Parameter(description = "Only entries labelled with this category, whole or by split") @RequestParam @Nullable UUID categoryId,
            @RequestParam @Nullable TransactionType type,
            @Parameter(description = "Inclusive lower bound on bookedOn") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @Parameter(description = "Inclusive upper bound on bookedOn") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
            @Parameter(description = "Full-text search over description, merchant and notes; supports quotes, OR and -exclusions") @RequestParam(name = "q") @Size(max = 200) @Nullable String search,
            @Parameter(description = "nextCursor from the previous page") @RequestParam @Nullable String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {

        FeedQuery query = new FeedQuery(
                principal.id(),
                accountId,
                categoryId,
                type,
                from,
                to,
                search,
                cursor == null ? null : FeedCursor.decode(cursor),
                limit);
        FeedPage page = transactions.feed(query);
        return new TransactionFeedResponse(
                page.items().stream().map(mapper::toItem).toList(),
                page.next() == null ? null : page.next().encode());
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get one entry, with notes and splits")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The entry"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "404", description = "No such entry for this caller", content = {})
    })
    public TransactionResponse get(@CurrentUser CurrentUserPrincipal principal, @PathVariable UUID transactionId) {
        return mapper.toResponse(transactions.get(principal.id(), transactionId));
    }

    @PatchMapping("/{transactionId}")
    @Operation(summary = "Relabel an entry", description = "Description, merchant, notes, tags, category and splits only. The monetary facts are immutable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated entry"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = {}),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "404", description = "No such entry or category for this caller", content = {}),
            @ApiResponse(responseCode = "422", description = "A domain rule was broken", content = {})
    })
    public TransactionResponse update(
            @CurrentUser CurrentUserPrincipal principal,
            @PathVariable UUID transactionId,
            @Valid @RequestBody UpdateTransactionRequest request) {
        return mapper.toResponse(transactions.update(principal.id(), transactionId, mapper.toCommand(request)));
    }

    @PostMapping("/{transactionId}/reverse")
    @Operation(summary = "Reverse an entry", description = "Appends an entry of the opposite direction on the same accounts, categories and date, and links the two.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The reversing entry"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "404", description = "No such entry for this caller", content = {}),
            @ApiResponse(responseCode = "409", description = "Already reversed", content = {}),
            @ApiResponse(responseCode = "422", description = "The entry is itself a reversal", content = {})
    })
    public ResponseEntity<TransactionResponse> reverse(
            @CurrentUser CurrentUserPrincipal principal,
            @PathVariable UUID transactionId,
            @Valid @RequestBody(required = false) @Nullable ReverseTransactionRequest request) {

        Transaction reversal = transactions.reverse(principal.id(), transactionId,
                request == null ? null : request.reason());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/transactions/{id}")
                .buildAndExpand(reversal.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(reversal));
    }

}
