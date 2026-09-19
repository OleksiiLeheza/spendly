package com.spendly.accountservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendly.accountservice.domain.Account;
import com.spendly.accountservice.domain.AccountType;
import com.spendly.accountservice.domain.Posting;
import com.spendly.accountservice.service.AccountService;
import com.spendly.accountservice.service.BalanceReconciliation;
import com.spendly.accountservice.service.OpenAccount;
import com.spendly.accountservice.service.PostingCommand;
import com.spendly.accountservice.service.PostingService;
import com.spendly.common.money.Money;
import com.spendly.common.web.ApiException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

@WithMockUser
class BalanceRecalculationTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);

    @Autowired
    private AccountService accounts;

    @Autowired
    private PostingService postings;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void balanceIsTheRunningSumOfPostings() {
        UUID owner = UUID.randomUUID();
        UUID account = open(owner, "USD");

        postings.post(new PostingCommand(owner, account, UUID.randomUUID(), Money.of("100.00", "USD"), TODAY));
        postings.post(new PostingCommand(owner, account, UUID.randomUUID(), Money.of("-30.25", "USD"), TODAY));

        assertThat(accounts.get(owner, account).getBalance()).isEqualTo(Money.of("69.75", "USD"));
    }

    @Test
    void redeliveringTheSameLedgerEntryDoesNotDoubleBook() {
        UUID owner = UUID.randomUUID();
        UUID account = open(owner, "USD");
        UUID ledgerEntry = UUID.randomUUID();
        PostingCommand command = new PostingCommand(owner, account, ledgerEntry, Money.of("100.00", "USD"), TODAY);

        Posting first = postings.post(command);
        Posting replayed = postings.post(command);

        assertThat(replayed.getId()).isEqualTo(first.getId());
        assertThat(accounts.get(owner, account).getBalance()).isEqualTo(Money.of("100.00", "USD"));
        assertThat(accounts.reconcile(owner, account).postings()).isEqualTo(1);
    }

    @Test
    void rejectsAPostingInAnotherCurrencyWithoutTouchingTheBalance() {
        UUID owner = UUID.randomUUID();
        UUID account = open(owner, "USD");
        postings.post(new PostingCommand(owner, account, UUID.randomUUID(), Money.of("10.00", "USD"), TODAY));

        assertThatThrownBy(() -> postings.post(
                new PostingCommand(owner, account, UUID.randomUUID(), Money.of("5.00", "EUR"), TODAY)))
                .isInstanceOf(ApiException.Unprocessable.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("EUR");

        assertThat(accounts.get(owner, account).getBalance()).isEqualTo(Money.of("10.00", "USD"));
    }

    @Test
    void archivedAccountsTakeNoPostings() {
        UUID owner = UUID.randomUUID();
        UUID account = open(owner, "USD");
        accounts.archive(owner, account);

        assertThatThrownBy(() -> postings.post(
                new PostingCommand(owner, account, UUID.randomUUID(), Money.of("1.00", "USD"), TODAY)))
                .isInstanceOf(ApiException.Unprocessable.class)
                .hasMessageContaining("archived");
    }

    @Test
    void reconciliationRepairsACachedBalanceThatDriftedFromTheLedger() {
        UUID owner = UUID.randomUUID();
        UUID account = open(owner, "USD");
        postings.post(new PostingCommand(owner, account, UUID.randomUUID(), Money.of("100.00", "USD"), TODAY));
        postings.post(new PostingCommand(owner, account, UUID.randomUUID(), Money.of("-30.25", "USD"), TODAY));

        BalanceReconciliation consistent = accounts.reconcile(owner, account);
        assertThat(consistent.wasConsistent()).isTrue();
        assertThat(consistent.drift()).isEqualTo(Money.zero("USD"));

        jdbc.update("UPDATE accounts SET balance_value = 999 WHERE id = ?", account);

        BalanceReconciliation repaired = accounts.reconcile(owner, account);
        assertThat(repaired.wasConsistent()).isFalse();
        assertThat(repaired.cachedBefore()).isEqualTo(Money.of("999.00", "USD"));
        assertThat(repaired.replayed()).isEqualTo(Money.of("69.75", "USD"));
        assertThat(repaired.drift()).isEqualTo(Money.of("929.25", "USD"));
        assertThat(repaired.postings()).isEqualTo(2);

        assertThat(accounts.get(owner, account).getBalance()).isEqualTo(Money.of("69.75", "USD"));
        assertThat(accounts.reconcile(owner, account).wasConsistent()).isTrue();
    }

    @Test
    void anotherOwnerCannotPostToTheAccount() {
        UUID owner = UUID.randomUUID();
        UUID account = open(owner, "USD");

        assertThatThrownBy(() -> postings.post(new PostingCommand(
                UUID.randomUUID(), account, UUID.randomUUID(), Money.of("1.00", "USD"), TODAY)))
                .isInstanceOf(ApiException.NotFound.class);
    }

    private UUID open(UUID owner, String currency) {
        Account account = accounts.open(owner, new OpenAccount("Test " + currency, AccountType.BANK, currency));
        return account.getId();
    }
}
