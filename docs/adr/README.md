# Architecture Decision Records

One file per decision, numbered in the order they were taken, never renumbered. A superseded
record stays in place with its status changed and a link to its successor, so the history of
*why* survives the change of *what*.

Format: [MADR](https://adr.github.io/madr/) — Context, Decision, Consequences, Alternatives.
Backlog tickets reference records as `ADR-NNNN`.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-monorepo-money-uuidv7.md) | Maven monorepo, BigDecimal money, UUIDv7 identifiers | Accepted |
| [0002](0002-postgres-over-mysql.md) | PostgreSQL over MySQL | Accepted |
| [0003](0003-cursor-pagination.md) | Cursor (keyset) pagination for the transaction feed | Accepted |
