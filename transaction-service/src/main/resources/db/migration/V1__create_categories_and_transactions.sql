-- The ledger (CLAUDE.md 1.3.3, 6.11) and the category tree it references.

CREATE TABLE categories (
    id                 UUID         NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,

    owner_id           UUID         NOT NULL,
    parent_id          UUID,
    name               VARCHAR(80)  NOT NULL,
    kind               VARCHAR(10)  NOT NULL,
    icon               VARCHAR(40),
    sort_order         INTEGER      NOT NULL DEFAULT 0,
    -- Identifies a seeded default (e.g. 'food.groceries') so a rename never loses which
    -- default it came from and seeding is idempotent per owner.
    seed_key           VARCHAR(60),
    archived_at        TIMESTAMPTZ,
    merged_into_id     UUID,

    created_by         UUID,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_modified_by   UUID,
    last_modified_at   TIMESTAMPTZ  NOT NULL,
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id),
    CONSTRAINT fk_categories_merged_into FOREIGN KEY (merged_into_id) REFERENCES categories (id),
    CONSTRAINT ck_categories_kind CHECK (kind IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_categories_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

-- Sibling names are unique per owner among live categories; the zero UUID stands in for
-- "no parent" so root categories take part in the same rule.
CREATE UNIQUE INDEX ux_categories_owner_parent_name
    ON categories (owner_id, COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), LOWER(name))
    WHERE archived_at IS NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX ux_categories_owner_seed ON categories (owner_id, seed_key) WHERE seed_key IS NOT NULL;
CREATE INDEX ix_categories_owner ON categories (owner_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_categories_parent ON categories (parent_id) WHERE parent_id IS NOT NULL;

-- Partitioned by month on booked_on from day one: this is the only table that grows without
-- bound, and retrofitting partitioning means downtime. Every unique constraint on a
-- partitioned table has to include the partition key, hence the composite primary key; the
-- application still treats id (a UUIDv7) as the identity.
CREATE TABLE transactions (
    id                          UUID          NOT NULL,
    version                     BIGINT        NOT NULL DEFAULT 0,

    owner_id                    UUID          NOT NULL,
    account_id                  UUID          NOT NULL,
    transfer_account_id         UUID,
    type                        VARCHAR(10)   NOT NULL,
    amount_value                NUMERIC(19,4) NOT NULL,
    amount_currency             CHAR(3)       NOT NULL,
    booked_on                   DATE          NOT NULL,
    description                 VARCHAR(200)  NOT NULL,
    merchant                    VARCHAR(120),
    notes                       TEXT,
    tags                        TEXT[]        NOT NULL DEFAULT '{}',
    category_id                 UUID,
    reverses_transaction_id     UUID,
    reversed_by_transaction_id  UUID,
    idempotency_key             VARCHAR(64),
    search_vector               TSVECTOR      GENERATED ALWAYS AS (
                                    to_tsvector('simple',
                                        COALESCE(description, '') || ' ' ||
                                        COALESCE(merchant, '') || ' ' ||
                                        COALESCE(notes, ''))
                                ) STORED,

    created_by                  UUID,
    created_at                  TIMESTAMPTZ   NOT NULL,
    last_modified_by            UUID,
    last_modified_at            TIMESTAMPTZ   NOT NULL,
    deleted_at                  TIMESTAMPTZ,

    CONSTRAINT pk_transactions PRIMARY KEY (id, booked_on),
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_transactions_type CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount_value > 0),
    CONSTRAINT ck_transactions_currency_is_iso4217 CHECK (amount_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_transactions_transfer_has_counterparty CHECK ((type = 'TRANSFER') = (transfer_account_id IS NOT NULL)),
    CONSTRAINT ck_transactions_transfer_between_two_accounts CHECK (transfer_account_id IS NULL OR transfer_account_id <> account_id),
    CONSTRAINT ck_transactions_transfer_uncategorised CHECK (type <> 'TRANSFER' OR category_id IS NULL)
) PARTITION BY RANGE (booked_on);

CREATE TABLE transactions_default PARTITION OF transactions DEFAULT;

DO $$
DECLARE
    month_start DATE := DATE '2024-01-01';
BEGIN
    WHILE month_start < DATE '2028-01-01' LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS transactions_%s PARTITION OF transactions FOR VALUES FROM (%L) TO (%L)',
            to_char(month_start, 'YYYY_MM'), month_start, month_start + INTERVAL '1 month');
        month_start := month_start + INTERVAL '1 month';
    END LOOP;
END $$;

-- The feed: newest first per owner, keyset-paginated on (booked_on, id).
CREATE INDEX ix_transactions_feed ON transactions (owner_id, booked_on DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_transactions_account ON transactions (owner_id, account_id, booked_on DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_transactions_category ON transactions (owner_id, category_id) WHERE category_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_transactions_search ON transactions USING GIN (search_vector);
CREATE INDEX ix_transactions_idempotency ON transactions (owner_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX ux_transactions_idempotency ON transactions (owner_id, idempotency_key, booked_on) WHERE idempotency_key IS NOT NULL;

-- Append-only, enforced where it cannot be bypassed. Categorisation, description, tags and
-- notes are labels and may change; the monetary facts may not. A correction is a reversing
-- entry (CLAUDE.md 1.3.3).
CREATE FUNCTION transactions_forbid_monetary_update() RETURNS trigger AS $$
BEGIN
    IF NEW.owner_id            IS DISTINCT FROM OLD.owner_id
       OR NEW.account_id          IS DISTINCT FROM OLD.account_id
       OR NEW.transfer_account_id IS DISTINCT FROM OLD.transfer_account_id
       OR NEW.type                IS DISTINCT FROM OLD.type
       OR NEW.amount_value        IS DISTINCT FROM OLD.amount_value
       OR NEW.amount_currency     IS DISTINCT FROM OLD.amount_currency
       OR NEW.booked_on           IS DISTINCT FROM OLD.booked_on
       OR NEW.reverses_transaction_id IS DISTINCT FROM OLD.reverses_transaction_id
    THEN
        RAISE EXCEPTION 'transactions are append-only: post a reversing entry instead of editing %', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_append_only
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION transactions_forbid_monetary_update();

CREATE FUNCTION transactions_forbid_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'transactions are append-only: post a reversing entry instead of deleting %', OLD.id
        USING ERRCODE = 'integrity_constraint_violation';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_no_delete
    BEFORE DELETE ON transactions
    FOR EACH ROW EXECUTE FUNCTION transactions_forbid_delete();

-- One purchase across several categories. The split amounts sum to the transaction amount,
-- which the service checks; the composite foreign key follows the partitioned parent's key.
CREATE TABLE transaction_splits (
    transaction_id     UUID          NOT NULL,
    booked_on          DATE          NOT NULL,
    position           INTEGER       NOT NULL,
    category_id        UUID          NOT NULL,
    amount_value       NUMERIC(19,4) NOT NULL,
    amount_currency    CHAR(3)       NOT NULL,
    note               VARCHAR(200),

    CONSTRAINT pk_transaction_splits PRIMARY KEY (transaction_id, position),
    CONSTRAINT fk_transaction_splits_transaction FOREIGN KEY (transaction_id, booked_on) REFERENCES transactions (id, booked_on),
    CONSTRAINT fk_transaction_splits_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_transaction_splits_amount_positive CHECK (amount_value > 0)
);

CREATE INDEX ix_transaction_splits_category ON transaction_splits (category_id);
