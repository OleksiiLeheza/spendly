# ADR-0002: PostgreSQL over MySQL

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-09-19 |
| **Ticket** | FI-28 |
| **Deciders** | Oleksii Leheza |

## Context

Every Spendly service owns one relational database (one logical database per service,
ADR-0001 §1). The candidates were the two mainstream open-source engines, PostgreSQL and
MySQL/MariaDB. The choice is made once: the ledger's storage features are exercised from the
first migration, and switching engines later means rewriting them.

The ledger in particular needs four things from its database:

1. **Exact decimal money** with a stated scale — `numeric(19,4)`, never floating point.
2. **A table that grows without bound** (`transactions`) which must stay fast for
   "newest first per owner" reads as it grows — partitioning by month from day one
   (CLAUDE.md 6.11).
3. **Full-text search** over description, merchant and notes in at least English and
   Ukrainian, without a second search system in the MVP.
4. **Invariants enforced where they cannot be bypassed**: append-only monetary columns,
   ISO-4217 currency codes, partial uniqueness for soft-deleted rows.

## Decision

**PostgreSQL 17** for every service database, run locally in Docker Compose and in tests
through Testcontainers (`postgres:17-alpine`).

What the ledger's first migration (`transaction-service` `V1`) already relies on, and why
each item tipped the decision:

| Need | PostgreSQL | MySQL 8 |
|---|---|---|
| Declarative range partitioning with a `DEFAULT` partition, indexes and row-level triggers inherited by every partition | Yes | Partitioning exists, but no default partition, no foreign keys to or from a partitioned table, and no triggers inherited per partition |
| Foreign key **from** a child table (`transaction_splits`) **to** a partitioned parent | Yes (composite key including the partition column) | Not supported |
| Generated `tsvector` column + GIN index; `websearch_to_tsquery` gives quotes, `OR` and `-exclusions` for free | Yes; the `simple` configuration tokenises Cyrillic and Latin alike | `FULLTEXT` exists, but no generated-column search vector, weaker query syntax, and InnoDB's tokeniser needs configuration per language |
| Partial unique indexes (`WHERE deleted_at IS NULL`) so a soft-deleted row releases its key | Yes | No partial indexes; needs a generated "live" column or application checks |
| Expression indexes (`LOWER(name)`, `COALESCE(parent_id, …)`) for case-insensitive sibling uniqueness | Yes | Functional indexes since 8.0.13, with restrictions |
| `CHECK` constraints that are actually enforced (`amount_value > 0`, `currency ~ '^[A-Z]{3}$'`) | Always | Only since 8.0.16; silently ignored before |
| Regular-expression `CHECK` for the currency code | Yes (`~`) | `REGEXP` in checks only from 8.0.16 |
| Row-value comparison for keyset pagination `(booked_on, id) < (:date, :id)` using the composite index | Yes, index-aware | Supported syntactically; the optimiser does not always use the index |
| `uuid` native type, `text[]` arrays for tags, `jsonb` for the outbox payload | Native | `BINARY(16)` plus helper functions; no arrays; JSON without `jsonb`-style indexing |
| `timestamptz` that stores an instant | Yes | `TIMESTAMP` is session-zone dependent; `DATETIME` has no zone |
| `SELECT … FOR UPDATE` on one row without gap locks | Yes | InnoDB gap/next-key locks under REPEATABLE READ make the account-balance lock (CLAUDE.md 6.9) lock more than one row |
| Transactional DDL — a failed Flyway migration rolls back cleanly | Yes | DDL commits implicitly; a half-applied migration must be repaired by hand |
| Debezium CDC source for the outbox (FI-47) | Logical decoding, `pgoutput` built in | Binlog, comparable |
| `pgvector` for the AI service retrieval (P4) | Extension | No equivalent in the same engine |

MySQL's advantages — a slightly simpler replication story for read replicas, wider hosting
familiarity — do not touch anything on this list.

## Consequences

- **+** The four ledger requirements above are met by the engine, with no second system and
  no application-level emulation.
- **+** Testcontainers runs the same image as production, so `ddl-auto=validate` and every
  migration are exercised on the real engine on every build.
- **+** One engine for relational data, full-text search, CDC and (later) vector retrieval.
- **−** PostgreSQL-specific SQL in migrations (`PARTITION BY`, `tsvector`, `text[]`, partial
  indexes, `DO $$ … $$`). Accepted: portability across engines was never a goal, and
  Flyway migrations are per-engine by nature.
- **−** Partition maintenance is our job: `V1` creates monthly partitions through 2027 plus a
  `DEFAULT` partition; a scheduled job must keep creating them ahead of time before 2028.
- **−** Connection-per-process model: pool sizes are set deliberately per service
  (CLAUDE.md 6.14) rather than relying on cheap threads.

## Alternatives considered

- **MySQL 8 / MariaDB.** Rejected for the partitioning, partial-index and check-constraint
  gaps in the table above — each would have to be emulated in the application, which is
  exactly where invariants stop being invariants.
- **One database engine per service ("polyglot persistence").** Rejected for now: no
  service has a need PostgreSQL cannot meet, and a second engine is a second thing to
  operate, back up and learn. Revisit only if a concrete service (search at scale, time
  series) outgrows it.
- **A hosted document store (MongoDB).** Rejected: a ledger is relational — accounts,
  categories and splits are joins with foreign keys, and the invariants are constraints.
