package com.spendly.accountservice.service;

import com.spendly.accountservice.domain.Account;
import com.spendly.accountservice.repository.AccountRepository;
import com.spendly.accountservice.repository.PostingRepository;
import com.spendly.common.money.Money;
import com.spendly.common.web.ApiException;
import java.time.Clock;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accounts;
    private final PostingRepository postings;
    private final Clock clock;

    public AccountService(AccountRepository accounts, PostingRepository postings, Clock clock) {
        this.accounts = accounts;
        this.postings = postings;
        this.clock = clock;
    }

    @Transactional
    public Account open(UUID ownerId, OpenAccount command) {
        Account account = Account.open(ownerId, command.name().strip(), command.type(),
                currency(command.currencyCode()));
        return accounts.save(account);
    }

    public Page<Account> list(UUID ownerId, boolean includeArchived, Pageable pageable) {
        return accounts.findAllOwned(ownerId, includeArchived, pageable);
    }

    public Account get(UUID ownerId, UUID accountId) {
        return accounts.findOwned(ownerId, accountId)
                .orElseThrow(() -> new ApiException.NotFound("Account", accountId));
    }

    @Transactional
    public Account archive(UUID ownerId, UUID accountId) {
        Account account = get(ownerId, accountId);
        account.archive(clock.instant());
        return account;
    }

    @Transactional
    public BalanceReconciliation reconcile(UUID ownerId, UUID accountId) {
        Account account = accounts.findOwnedForUpdate(ownerId, accountId)
                .orElseThrow(() -> new ApiException.NotFound("Account", accountId));

        Money cached = account.getBalance();
        Money replayed = Money.of(postings.sumOwned(ownerId, accountId), account.currency());
        BalanceReconciliation result = new BalanceReconciliation(cached, replayed,
                postings.countOwned(ownerId, accountId));

        if (!result.wasConsistent()) {
            log.warn("Account {} cached balance {} drifted from the ledger replay {}; repaired", accountId, cached,
                    replayed);
            account.replaceBalance(replayed);
        }
        return result;
    }

    private static Currency currency(String code) {
        try {
            return Currency.getInstance(code.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException.Unprocessable("'" + code + "' is not a known ISO-4217 currency");
        }
    }
}
