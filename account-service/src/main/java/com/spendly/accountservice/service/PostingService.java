package com.spendly.accountservice.service;

import com.spendly.accountservice.domain.Account;
import com.spendly.accountservice.domain.Posting;
import com.spendly.accountservice.domain.TransferLegs;
import com.spendly.accountservice.repository.AccountRepository;
import com.spendly.accountservice.repository.PostingRepository;
import com.spendly.common.web.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PostingService {

    private final AccountRepository accounts;
    private final PostingRepository postings;

    public PostingService(AccountRepository accounts, PostingRepository postings) {
        this.accounts = accounts;
        this.postings = postings;
    }

    @Transactional
    public Posting post(PostingCommand command) {
        return postings.findOwnedByLedgerEntry(command.ownerId(), command.accountId(), command.ledgerEntryId())
                .orElseGet(() -> {
                    Account account = lock(command.ownerId(), command.accountId());
                    return postings.save(account.post(command.ledgerEntryId(), command.amount(), command.bookedOn()));
                });
    }

    @Transactional
    public TransferLegs transfer(TransferCommand command) {
        if (command.fromAccountId().equals(command.toAccountId())) {
            throw new ApiException.Unprocessable("A transfer needs two different accounts");
        }
        if (!command.amount().isPositive()) {
            throw new ApiException.Unprocessable("A transfer amount must be positive");
        }

        Optional<Posting> debitApplied = postings.findOwnedByLedgerEntry(command.ownerId(), command.fromAccountId(),
                command.transferId());
        Optional<Posting> creditApplied = postings.findOwnedByLedgerEntry(command.ownerId(), command.toAccountId(),
                command.transferId());
        if (debitApplied.isPresent() != creditApplied.isPresent()) {
            throw new ApiException.Conflict("Transfer " + command.transferId() + " was only half applied");
        }
        if (debitApplied.isPresent()) {
            return new TransferLegs(debitApplied.orElseThrow(), creditApplied.orElseThrow());
        }

        boolean fromFirst = command.fromAccountId().compareTo(command.toAccountId()) < 0;
        Account first = lock(command.ownerId(), fromFirst ? command.fromAccountId() : command.toAccountId());
        Account second = lock(command.ownerId(), fromFirst ? command.toAccountId() : command.fromAccountId());
        Account from = fromFirst ? first : second;
        Account to = fromFirst ? second : first;

        Posting debit = from.post(command.transferId(), command.amount().negated(), command.bookedOn());
        Posting credit = to.post(command.transferId(), command.amount(), command.bookedOn());
        TransferLegs legs = new TransferLegs(debit, credit);

        postings.save(legs.debit());
        postings.save(legs.credit());
        return legs;
    }

    private Account lock(UUID ownerId, UUID accountId) {
        return accounts.findOwnedForUpdate(ownerId, accountId)
                .orElseThrow(() -> new ApiException.NotFound("Account", accountId));
    }
}
