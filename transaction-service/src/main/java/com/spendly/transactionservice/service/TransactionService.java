package com.spendly.transactionservice.service;

import com.spendly.common.web.ApiException;
import com.spendly.transactionservice.domain.CategoryKind;
import com.spendly.transactionservice.domain.Split;
import com.spendly.transactionservice.domain.Transaction;
import com.spendly.transactionservice.domain.FeedPage;
import com.spendly.transactionservice.domain.FeedQuery;
import com.spendly.transactionservice.repository.TransactionFeedRepository;
import com.spendly.transactionservice.repository.TransactionRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class TransactionService {

    static final int MAX_TAGS = 20;
    static final int MAX_SPLITS = 20;

    private final TransactionRepository transactions;
    private final TransactionFeedRepository feed;
    private final CategoryService categories;

    public TransactionService(
            TransactionRepository transactions, TransactionFeedRepository feed, CategoryService categories) {
        this.transactions = transactions;
        this.feed = feed;
        this.categories = categories;
    }

    @Transactional
    public PostedTransaction post(UUID ownerId, PostTransaction command, @Nullable String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Transaction> replayed = transactions.findOwnedByIdempotencyKey(ownerId, idempotencyKey);
            if (replayed.isPresent()) {
                return new PostedTransaction(replayed.orElseThrow(), true);
            }
        }
        Transaction transaction = Transaction.post(
                ownerId,
                command.accountId(),
                command.transferAccountId(),
                command.type(),
                command.amount(),
                command.bookedOn(),
                command.description().strip(),
                idempotencyKey);
        transaction.setMerchant(blankToNull(command.merchant()));
        transaction.setNotes(blankToNull(command.notes()));
        transaction.setTags(normaliseTags(command.tags()));
        categorise(ownerId, transaction, command.categoryId(), command.splits());
        try {
            return new PostedTransaction(transactions.saveAndFlush(transaction), false);
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey == null) {
                throw ex;
            }
            throw new ApiException.Conflict("Idempotency-Key " + idempotencyKey + " is already being processed");
        }
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
            transaction.setMerchant(blankToNull(command.merchant()));
        }
        if (command.notes() != null) {
            transaction.setNotes(blankToNull(command.notes()));
        }
        if (command.tags() != null) {
            transaction.setTags(normaliseTags(command.tags()));
        }
        if (command.clearsCategory()) {
            transaction.categorise(null);
        } else if (command.splits() != null) {
            categorise(ownerId, transaction, null, command.splits());
        } else if (command.categoryId() != null) {
            categorise(ownerId, transaction, command.categoryId(), List.of());
        }
        return transaction;
    }

    @Transactional
    public Transaction reverse(UUID ownerId, UUID transactionId, @Nullable String reason) {
        Transaction original = owned(ownerId, transactionId);
        Transaction reversal = original.reversal("Reversal of: " + original.getDescription(), blankToNull(reason));
        reversal = transactions.saveAndFlush(reversal);
        original.markReversedBy(reversal);
        return reversal;
    }

    private void categorise(UUID ownerId, Transaction transaction, @Nullable UUID categoryId,
            List<SplitCommand> splits) {
        if (transaction.isTransfer()) {
            if (categoryId != null || !splits.isEmpty()) {
                throw new ApiException.Unprocessable("A transfer is not categorised");
            }
            return;
        }
        if (categoryId != null && !splits.isEmpty()) {
            throw new ApiException.Unprocessable("Give either a category or splits, not both");
        }
        if (splits.size() > MAX_SPLITS) {
            throw new ApiException.Unprocessable("At most " + MAX_SPLITS + " splits per transaction");
        }
        CategoryKind kind = CategoryKind.forTransaction(transaction.getType());
        if (categoryId != null) {
            categories.requireOwnedLive(ownerId, Set.of(categoryId), kind);
            transaction.categorise(categoryId);
            return;
        }
        if (!splits.isEmpty()) {
            Set<UUID> ids = splits.stream().map(SplitCommand::categoryId).collect(Collectors.toSet());
            categories.requireOwnedLive(ownerId, ids, kind);
            List<Split> rows = splits.stream()
                    .map(split -> transaction.split(split.categoryId(), split.amount(), blankToNull(split.note())))
                    .toList();
            transaction.split(rows);
        }
    }

    private Transaction owned(UUID ownerId, UUID transactionId) {
        return transactions.findOwned(ownerId, transactionId)
                .orElseThrow(() -> new ApiException.NotFound("Transaction", transactionId));
    }

    private static List<String> normaliseTags(List<String> tags) {
        Set<String> unique = new HashSet<>();
        List<String> normalised = tags.stream()
                .map(tag -> tag.strip().toLowerCase(java.util.Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .filter(unique::add)
                .toList();
        if (normalised.size() > MAX_TAGS) {
            throw new ApiException.Unprocessable("At most " + MAX_TAGS + " tags per transaction");
        }
        return normalised;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
