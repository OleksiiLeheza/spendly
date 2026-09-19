package com.spendly.accountservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendly.accountservice.domain.AccountType;
import com.spendly.accountservice.domain.TransferLegs;
import com.spendly.accountservice.service.AccountService;
import com.spendly.accountservice.service.OpenAccount;
import com.spendly.accountservice.service.PostingService;
import com.spendly.accountservice.service.TransferCommand;
import com.spendly.common.money.Money;
import com.spendly.common.web.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

@WithMockUser
class TransferPairInvariantTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);

    @Autowired
    private AccountService accounts;

    @Autowired
    private PostingService postings;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aTransferBetweenOwnAccountsNetsToZero() {
        UUID owner = UUID.randomUUID();
        UUID from = open(owner, "USD");
        UUID to = open(owner, "USD");
        UUID transfer = UUID.randomUUID();

        TransferLegs legs = postings
                .transfer(new TransferCommand(owner, transfer, from, to, Money.of("40.00", "USD"), TODAY));

        assertThat(legs.debit().getAmount()).isEqualTo(Money.of("-40.00", "USD"));
        assertThat(legs.credit().getAmount()).isEqualTo(Money.of("40.00", "USD"));
        assertThat(legs.debit().getAmount().plus(legs.credit().getAmount())).isEqualTo(Money.zero("USD"));
        assertThat(legs.debit().getLedgerEntryId()).isEqualTo(transfer);
        assertThat(legs.credit().getLedgerEntryId()).isEqualTo(transfer);

        assertThat(accounts.get(owner, from).getBalance()).isEqualTo(Money.of("-40.00", "USD"));
        assertThat(accounts.get(owner, to).getBalance()).isEqualTo(Money.of("40.00", "USD"));

        BigDecimal netInLedger = jdbc.queryForObject(
                "SELECT SUM(amount_value) FROM postings WHERE ledger_entry_id = ?", BigDecimal.class, transfer);
        assertThat(netInLedger).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void redeliveringATransferAppliesItOnce() {
        UUID owner = UUID.randomUUID();
        UUID from = open(owner, "USD");
        UUID to = open(owner, "USD");
        TransferCommand command = new TransferCommand(owner, UUID.randomUUID(), from, to, Money.of("15.00", "USD"),
                TODAY);

        TransferLegs first = postings.transfer(command);
        TransferLegs replayed = postings.transfer(command);

        assertThat(replayed.debit().getId()).isEqualTo(first.debit().getId());
        assertThat(replayed.credit().getId()).isEqualTo(first.credit().getId());
        assertThat(accounts.get(owner, from).getBalance()).isEqualTo(Money.of("-15.00", "USD"));
        assertThat(accounts.get(owner, to).getBalance()).isEqualTo(Money.of("15.00", "USD"));
    }

    @Test
    void rejectsATransferToTheSameAccount() {
        UUID owner = UUID.randomUUID();
        UUID account = open(owner, "USD");

        assertThatThrownBy(() -> postings.transfer(
                new TransferCommand(owner, UUID.randomUUID(), account, account, Money.of("1.00", "USD"), TODAY)))
                .isInstanceOf(ApiException.Unprocessable.class)
                .hasMessageContaining("two different accounts");
    }

    @Test
    void rejectsANonPositiveAmount() {
        UUID owner = UUID.randomUUID();
        UUID from = open(owner, "USD");
        UUID to = open(owner, "USD");

        assertThatThrownBy(() -> postings.transfer(
                new TransferCommand(owner, UUID.randomUUID(), from, to, Money.of("-1.00", "USD"), TODAY)))
                .isInstanceOf(ApiException.Unprocessable.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> postings.transfer(
                new TransferCommand(owner, UUID.randomUUID(), from, to, Money.zero("USD"), TODAY)))
                .isInstanceOf(ApiException.Unprocessable.class);
    }

    @Test
    void aCrossCurrencyTransferIsRejectedAndLeavesBothBalancesUntouched() {
        UUID owner = UUID.randomUUID();
        UUID usd = open(owner, "USD");
        UUID eur = open(owner, "EUR");

        assertThatThrownBy(() -> postings.transfer(
                new TransferCommand(owner, UUID.randomUUID(), usd, eur, Money.of("10.00", "USD"), TODAY)))
                .isInstanceOf(ApiException.Unprocessable.class)
                .hasMessageContaining("EUR");

        assertThat(accounts.get(owner, usd).getBalance()).isEqualTo(Money.zero("USD"));
        assertThat(accounts.get(owner, eur).getBalance()).isEqualTo(Money.zero("EUR"));
        assertThat(accounts.reconcile(owner, usd).postings()).isZero();
    }

    @Test
    void aTransferIntoSomeoneElsesAccountIsNotFoundAndRollsBack() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID alicesAccount = open(alice, "USD");
        UUID bobsAccount = open(bob, "USD");

        assertThatThrownBy(() -> postings.transfer(new TransferCommand(
                alice, UUID.randomUUID(), alicesAccount, bobsAccount, Money.of("10.00", "USD"), TODAY)))
                .isInstanceOf(ApiException.NotFound.class);

        assertThat(accounts.get(alice, alicesAccount).getBalance()).isEqualTo(Money.zero("USD"));
        assertThat(accounts.get(bob, bobsAccount).getBalance()).isEqualTo(Money.zero("USD"));
    }

    @Test
    void aTransferOutOfAnArchivedAccountIsRejected() {
        UUID owner = UUID.randomUUID();
        UUID from = open(owner, "USD");
        UUID to = open(owner, "USD");
        accounts.archive(owner, from);

        assertThatThrownBy(() -> postings.transfer(
                new TransferCommand(owner, UUID.randomUUID(), from, to, Money.of("10.00", "USD"), TODAY)))
                .isInstanceOf(ApiException.Unprocessable.class)
                .hasMessageContaining("archived");
        assertThat(accounts.get(owner, to).getBalance()).isEqualTo(Money.zero("USD"));
    }

    private UUID open(UUID owner, String currency) {
        return accounts.open(owner, new OpenAccount("Test " + currency, AccountType.BANK, currency)).getId();
    }
}
