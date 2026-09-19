# ADR-0001: Maven monorepo, BigDecimal money, UUIDv7 identifiers

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-09-19 |
| **Ticket** | FI-12 |
| **Deciders** | Oleksii Leheza |

Three foundation decisions that every later module inherits. They are recorded together
because they were taken together, before the first service existed, and because each one is
expensive to reverse once data exists.

---

## 1. One Maven monorepo, many deployables

### Context

Spendly is a distributed system of a dozen Spring Boot services plus an Angular SPA, built by
one person for a diploma. The distribution is part of the deliverable, so the services must
stay genuinely independent — separate databases, separate deployables, no shared tables. At
the same time a single developer cannot afford twelve repositories' worth of version drift,
duplicated build configuration and cross-repo pull requests for one feature.

### Decision

A single Git repository with a Maven aggregator (`pom.xml`) that is also the shared parent.
Every service is a module and a separate deployable. Shared code ships only through two
libraries:

- `common` — web, security, money and persistence primitives, wired by Spring Boot
  auto-configuration. Spring dependencies are `provided`, so a non-web consumer is not forced
  into MVC.
- `events` — the Kafka event contracts. **No framework dependencies at all**, enforced by an
  ArchUnit test (`ContractPurityTest`), so no consumer inherits a transitive Spring or Jackson.

`spendly-bom` publishes the version set for external consumers and is never imported by a
sibling module.

The dependency direction is a build failure, not a convention:

```
service ──► common ──► (Spring, provided)
service ──► events ──► (JSpecify only)
common  ──X events
events  ──X anything
service ──X service
```

Cross-cutting quality gates (Spotless, Error Prone + NullAway, SpotBugs + find-sec-bugs,
Modernizer, Enforcer, JaCoCo) are declared once in the parent and inherited by every module,
which is the concrete payoff of the monorepo: a rule added once applies everywhere.

### Consequences

- **+** One `./mvnw verify` proves the whole system compiles and passes together; a
  contract change in `events` breaks its consumers in the same build, not in production.
- **+** One version of Spring Boot, Java and every quality plugin, pinned in one place.
- **+** Atomic commits across a producer and its consumer.
- **−** The repository grows with every service; CI must learn to build only affected
  modules (`-pl <module> -am`) as the reactor grows.
- **−** The monorepo makes it *easy* to share code that should not be shared. The ArchUnit
  rules and the `service ──X service` prohibition exist precisely to counter that pull.

### Alternatives considered

- **One repository per service.** The textbook microservice shape. Rejected for a solo
  diploma project: the coordination cost lands entirely on one person, and version drift
  between `events` copies is the most likely way the system would rot.
- **A modular monolith, split later.** Cheaper to start, but "split later" is the phase that
  never comes, and the distribution *is* the deliverable here.
- **Gradle multi-project.** Comparable capability; Maven was chosen for the Spring Boot
  parent POM, the wider Spring documentation coverage and the maintainer's fluency.

---

## 2. Money is `BigDecimal` + `java.util.Currency`, never `double`

### Context

A personal-finance ledger sums thousands of small amounts, splits purchases across
categories and reconciles balances against a full replay. Binary floating point cannot
represent `0.10` exactly; `0.1 + 0.2 != 0.3`, and a ledger built on it drifts by a cent
somewhere every day, silently. Currencies also disagree about decimal places (USD 2, JPY 0,
BHD 3), and a bare number with the currency held "somewhere else" is a bug waiting for a
join to lose it.

### Decision

The single money type is `com.spendly.common.money.Money`, a record of `BigDecimal amount`
and `java.util.Currency currency`.

- **Scale rule.** On construction the amount is normalised to the currency's own fraction
  digits (`Currency.getDefaultFractionDigits()`, pseudo-currencies clamped to 0). This is
  what makes record equality correct — `BigDecimal.equals` is scale-sensitive, so `10.0` and
  `10.00` would otherwise differ.
- **Rounding policy is `HALF_EVEN`** (banker's rounding). `HALF_UP` biases every tie upward
  and accumulates a systematic drift across a ledger; half-even spreads ties both ways.
  `MoneyTest` proves that 30 additions of `0.005` sum to zero, not `0.15`.
- **Cross-currency arithmetic throws** `CurrencyMismatchException`. Conversion is an explicit
  operation with a recorded rate and rate date (the `fx-service`), never an implicit cast.
- **Splitting uses `Money.allocate(n)`**, which distributes the indivisible remainder one
  minor unit at a time so no cent is ever lost or invented.
- **Persistence:** `Money` is a JPA `@Embeddable` mapped onto a column pair
  `<name>_value numeric(19,4)` + `<name>_currency char(3)`. The currency is always stored
  next to the amount. `numeric`, never `float8`.
- **Wire format:** `{"amount": "10.50", "currency": "USD"}` — the amount is a decimal
  *string*, because a JSON number is parsed through an IEEE-754 double by every JavaScript
  runtime before `decimal.js` can see it. The Angular client does its arithmetic in
  `decimal.js`; it never touches `number` for money either.
- **Events** carry `BigDecimal amount` + `String currencyCode` rather than `Money`, because
  `events` may not depend on `common` (see §1).

### Consequences

- **+** Exact arithmetic, provable rounding, no silent currency mixing.
- **+** One representation across the database, the API and the client.
- **−** `BigDecimal` is slower and more verbose than a primitive. Irrelevant at this scale;
  and every arithmetic method on `Money` hides the verbosity.
- **−** ISO-4217 only. Crypto assets have no `java.util.Currency`, which is one more reason
  they are an explicit non-goal.

### Alternatives considered

- **`double` / `float`.** Rejected outright: not exact for decimal fractions.
- **Minor-unit `long` (cents).** Exact and fast, and the right choice for the *client*
  (`decimal.js` or integer minor units). Rejected on the server because the scale is then
  implicit in the code that reads the column, which is exactly the "currency held somewhere
  else" failure; and because 3-decimal currencies make "cents" a lie.
- **JSR 354 / Moneta (`javax.money`).** A full implementation of the same idea with rounding
  providers, formatting and exchange-rate SPIs. Rejected as more surface than the project
  needs; a 200-line record covers every operation the ledger performs, and is trivially
  auditable in a defence.

---

## 3. Primary keys are UUIDs, generated as UUIDv7 in the application

### Context

Every table in every service needs a primary key that is safe to expose in a URL, unique
across services without coordination (a transfer references accounts from another service's
database), and cheap to insert into a table that only grows. A `bigint` sequence fails the
first two; a random UUIDv4 fails the third: v4 scatters inserts across the whole B-tree, so
the index never stays in cache and the pages fragment as the ledger grows.

### Decision

Primary keys are `uuid` columns. Values are **RFC 9562 version 7** — a 48-bit Unix
millisecond timestamp followed by random bits — generated **in the application** by
`com.spendly.common.id.UuidV7` and wired into every entity through Hibernate's
`@UuidGenerator(algorithm = UuidV7Generator.class)` on the shared `BaseEntity`.

Generating in Java rather than the database because:

- PostgreSQL 17 has no native `uuidv7()`; that arrived in 18.
- The id is known *before* the insert, so an aggregate can reference its own children, an
  event can name its aggregate, and an idempotency key can be computed, all without a
  round-trip.

Within one millisecond the generator increments a 12-bit counter in `rand_a` instead of
re-randomising, so ids created in the same millisecond still sort in creation order.

A sequential `bigint` id is never exposed in an API, because it leaks row counts and invites
enumeration.

### Consequences

- **+** Time-ordered: inserts land at the right edge of the index, and "most recent first"
  becomes a range scan.
- **+** Globally unique with no coordination; safe across service boundaries and in URLs.
- **+** The creation instant is recoverable from the id, which helps debugging.
- **−** 16 bytes per key instead of 8. Accepted; the ledger's growth is bounded by human
  spending, not machine output.
- **−** The timestamp in the id is visible. Acceptable for this domain — an account's
  creation time is not a secret — but it is a reason not to use UUIDv7 for anything that
  must not reveal *when*.

### Alternatives considered

- **UUIDv4.** Unique and unordered. Rejected for index locality on the only table that grows
  without bound (`transaction`, partitioned by month).
- **`bigint` sequence.** Compact and ordered, but not unique across services and unsafe to
  expose. Rejected.
- **Hibernate's `UuidGenerator.Style.TIME`.** A version-1 variant that encodes the host's
  address into the high bits — leaks infrastructure into public identifiers. Rejected.
- **ULID / Snowflake ids.** Same idea as v7 (time-ordered, sortable) but non-standard
  encodings that Postgres' `uuid` type cannot store natively. v7 is the standardised form.
