package com.spendly.accountservice.api;

import com.spendly.accountservice.api.dto.AccountResponse;
import com.spendly.accountservice.api.dto.BalanceReconciliationResponse;
import com.spendly.accountservice.api.dto.OpenAccountRequest;
import com.spendly.accountservice.api.dto.PageResponse;
import com.spendly.accountservice.api.dto.ShareAccountRequest;
import com.spendly.accountservice.api.mapper.AccountMapper;
import com.spendly.accountservice.domain.Account;
import com.spendly.accountservice.service.AccountService;
import com.spendly.common.security.CurrentUser;
import com.spendly.common.security.CurrentUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(value = "/api/v1/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Accounts", description = "The caller's accounts. Balances are derived from the ledger, never set by hand.")
@SecurityRequirement(name = "bearer-jwt")
public class AccountController {

    private final AccountService accounts;
    private final AccountMapper mapper;

    public AccountController(AccountService accounts, AccountMapper mapper) {
        this.accounts = accounts;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Open an account", description = "Starts at a zero balance in the given currency.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The new account"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = {}),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "422", description = "Unknown currency", content = {})
    })
    public ResponseEntity<AccountResponse> open(
            @CurrentUser CurrentUserPrincipal principal, @Valid @RequestBody OpenAccountRequest request) {

        Account account = accounts.open(principal.id(), mapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(account.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(account));
    }

    @GetMapping
    @Operation(summary = "List the caller's accounts, own and shared with a household they belong to")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of accounts"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {})
    })
    public PageResponse<AccountResponse> list(
            @CurrentUser CurrentUserPrincipal principal,
            @Parameter(description = "Also return archived accounts") @RequestParam(defaultValue = "false") boolean includeArchived,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {

        return PageResponse.of(accounts.list(principal, includeArchived, pageable).map(mapper::toResponse));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get one account the caller owns or that is shared with their household")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The account"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "404", description = "No such account for this caller", content = {})
    })
    public AccountResponse get(@CurrentUser CurrentUserPrincipal principal, @PathVariable UUID accountId) {
        return mapper.toResponse(accounts.get(principal, accountId));
    }

    @PutMapping(value = "/{accountId}/sharing", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "shareAccount", summary = "Share the account with one of the caller's households", description = "Members of that household can read the account; only the owner changes it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The shared account"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = {}),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "403", description = "The caller can read this account but does not own it", content = {}),
            @ApiResponse(responseCode = "404", description = "No such account for this caller", content = {}),
            @ApiResponse(responseCode = "422", description = "The caller is not a member of that household", content = {})
    })
    public AccountResponse share(@CurrentUser CurrentUserPrincipal principal, @PathVariable UUID accountId,
            @Valid @RequestBody ShareAccountRequest request) {
        return mapper.toResponse(accounts.share(principal, accountId, request.householdId()));
    }

    @DeleteMapping("/{accountId}/sharing")
    @Operation(operationId = "unshareAccount", summary = "Stop sharing the account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The account, no longer shared"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "403", description = "The caller can read this account but does not own it", content = {}),
            @ApiResponse(responseCode = "404", description = "No such account for this caller", content = {})
    })
    public AccountResponse unshare(@CurrentUser CurrentUserPrincipal principal, @PathVariable UUID accountId) {
        return mapper.toResponse(accounts.unshare(principal.id(), accountId));
    }

    @PostMapping("/{accountId}/archive")
    @Operation(summary = "Archive an account", description = "Archived accounts keep their history and balance but accept no further postings. Idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The archived account"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "403", description = "The caller can read this account but does not own it", content = {}),
            @ApiResponse(responseCode = "404", description = "No such account for this caller", content = {})
    })
    public AccountResponse archive(@CurrentUser CurrentUserPrincipal principal, @PathVariable UUID accountId) {
        return mapper.toResponse(accounts.archive(principal.id(), accountId));
    }

    @PostMapping("/{accountId}/balance/reconcile")
    @Operation(summary = "Reconcile the cached balance against a full replay of the account's postings", description = "Repairs the cached balance when it drifted and reports the difference.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The reconciliation result"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "403", description = "The caller can read this account but does not own it", content = {}),
            @ApiResponse(responseCode = "404", description = "No such account for this caller", content = {})
    })
    public BalanceReconciliationResponse reconcile(
            @CurrentUser CurrentUserPrincipal principal, @PathVariable UUID accountId) {
        return mapper.toResponse(accounts.reconcile(principal.id(), accountId));
    }
}
