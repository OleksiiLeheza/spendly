package com.spendly.transactionservice.service;

import com.spendly.common.web.ApiException;
import com.spendly.events.Allocation;
import com.spendly.transactionservice.domain.FeedPage;
import com.spendly.transactionservice.domain.FeedQuery;
import com.spendly.transactionservice.domain.Transaction;
import com.spendly.transactionservice.repository.TransactionFeedRepository;
import com.spendly.transactionservice.repository.TransactionRepository;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class TransactionService {

    private final TransactionRepository transactions;
    private final TransactionFeedRepository feed;
    private final TransactionPoster poster;
    private final LedgerEvents events;

    public TransactionService(
            TransactionRepository transactions, TransactionFeedRepository feed, TransactionPoster poster,
            LedgerEvents events) {
        this.transactions = transactions;
        this.feed = feed;
        this.poster = poster;
        this.events = events;
    }

    @Transactional
    @PreAuthorize("#command.type().name() != 'TRANSFER' or @stepUp.fresh(authentication)")
    public PostedTransaction post(UUID ownerId, PostTransaction command, @Nullable String idempotencyKey) {
        return poster.post(ownerId, command, idempotencyKey);
    }

    public Transaction get(UUID ownerId, UUID transactionId) {
        return owned(ownerId, transactionId);
    }

    public FeedPage feed(FeedQuery query) {
        return feed.feed(query);
    }

    @Transactional
    public Transaction update(UUID ownerId, UUID transactionId, UpdateTransaction command) {
        Transaction transaction = owned(ownerId, transactionId);
        if (command.description() != null) {
            String description = command.description().strip();
            if (description.isEmpty()) {
                throw new ApiException.Unprocessable("The description cannot be blank");
            }
            transaction.describe(description);
        }
        if (command.merchant() != null) {
            transaction.setMerchant(TransactionPoster.blankToNull(command.merchant()));
        }
        if (command.notes() != null) {
            transaction.setNotes(TransactionPoster.blankToNull(command.notes()));
        }
        if (command.tags() != null) {
            transaction.setTags(TransactionPoster.normaliseTags(command.tags()));
        }
        List<Allocation> before = LedgerEvents.allocationsOf(transaction);
        if (command.clearsCategory()) {
            transaction.categorise(null);
        } else if (command.splits() != null) {
            poster.categorise(ownerId, transaction, null, command.splits());
        } else if (command.categoryId() != null) {
            poster.categorise(ownerId, transaction, command.categoryId(), List.of());
        }
        if (!before.equals(LedgerEvents.allocationsOf(transaction))) {
            events.categorised(transaction);
        }
        return transaction;
    }

    @Transactional
    @PreAuthorize("@stepUp.fresh(authentication)")
    public Transaction reverse(UUID ownerId, UUID transactionId, @Nullable String reason) {
        Transaction original = owned(ownerId, transactionId);
        Transaction reversal = original.reversal("Reversal of: " + original.getDescription(),
                TransactionPoster.blankToNull(reason));
        reversal = transactions.saveAndFlush(reversal);
        original.markReversedBy(reversal);
        events.created(reversal);
        return reversal;
    }

    private Transaction owned(UUID ownerId, UUID transactionId) {
        return transactions.findOwned(ownerId, transactionId)
                .orElseThrow(() -> new ApiException.NotFound("Transaction", transactionId));
    }
}
