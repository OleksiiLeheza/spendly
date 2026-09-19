-- Accounts and the per-account ledger projection their balances are derived from.
--
-- balance_value is a cache of SUM(postings.amount_value) for the account, maintained in the
-- same transaction as every posting and repairable by replay (CLAUDE.md 1.3.4). The account's
-- currency is the currency of that balance; every posting must be booked in it.

CREATE TABLE accounts (
    id                 UUID          NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,

    owner_id           UUID          NOT NULL,
    name               VARCHAR(100)  NOT NULL,
    type               VARCHAR(20)   NOT NULL,
    balance_value      NUMERIC(19,4) NOT NULL DEFAULT 0,
    balance_currency   CHAR(3)       NOT NULL,
    archived_at        TIMESTAMPTZ,

    created_by         UUID,
    created_at         TIMESTAMPTZ   NOT NULL,
    last_modified_by   UUID,
    last_modified_at   TIMESTAMPTZ   NOT NULL,
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT ck_accounts_type CHECK (type IN ('CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'BANK', 'SAVINGS', 'INVESTMENT')),
    CONSTRAINT ck_accounts_currency_is_iso4217 CHECK (balance_currency ~ '^[A-Z]{3}$')
);

-- Every read is scoped by owner (CLAUDE.md 1.3.5); the live set is the default listing.
CREATE INDEX ix_accounts_owner_live ON accounts (owner_id, created_at) WHERE deleted_at IS NULL;

-- Append-only: no version, no last_modified, no deleted_at. A wrong posting is corrected by
-- a reversing posting from the ledger, never by editing this row.
CREATE TABLE postings (
    id                 UUID          NOT NULL,
    account_id         UUID          NOT NULL,
    owner_id           UUID          NOT NULL,
    ledger_entry_id    UUID          NOT NULL,
    amount_value       NUMERIC(19,4) NOT NULL,
    amount_currency    CHAR(3)       NOT NULL,
    booked_on          DATE          NOT NULL,
    recorded_by        UUID,
    recorded_at        TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_postings PRIMARY KEY (id),
    CONSTRAINT fk_postings_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT ck_postings_currency_is_iso4217 CHECK (amount_currency ~ '^[A-Z]{3}$'),
    -- One ledger entry lands on one account at most once, so a redelivered event is a no-op
    -- rather than a double booking (CLAUDE.md 6.6).
    CONSTRAINT ux_postings_ledger_entry_account UNIQUE (ledger_entry_id, account_id)
);

CREATE INDEX ix_postings_account_booked_on ON postings (account_id, booked_on);
CREATE INDEX ix_postings_owner ON postings (owner_id);
