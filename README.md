# Spendly

Personal finance platform: expenses, budgets, goals and shared/split expenses.
Java 25 + Spring Boot 4 on the back end, Angular 22 + Angular Material on the front,
its own `auth-service` for sign-in (password + authenticator app, or Google).

Work is tracked in Jira project **FI (Financial)**.

## Layout

```
spendly/
├── pom.xml              aggregator + shared parent (Boot 4.1.1, Java 25, JSpecify)
├── spendly-bom/         published BOM for consumers outside the monorepo
├── common/              ProblemDetail handling, CurrentUser resolver, correlation-id filter,
│                        Money value object (JPA embeddable), audited entity base classes,
│                        UUIDv7 generator
├── events/              contract-only Kafka event schemas (no framework - enforced by ArchUnit)
├── user-service/        user profiles, Flyway migrations, OAuth2 resource server
├── account-service/     accounts, ledger-derived balances, transfer-pair invariant
├── transaction-service/ the append-only ledger (partitioned), splits, categories, feed with keyset paging + full-text
├── budget-service/      monthly limits per category; spend projected from ledger events, threshold alerts
├── recurring-service/   repeat rules + the scheduler that posts them, upcoming-payment calendar, detection of charges that repeat (Kafka Streams)
├── goal-service/        savings goals: round-ups from the ledger events, scheduled contributions, a forecast
├── notification-service/ inbox, email and web push, preferences, quiet hours, the weekly summary
├── import-service/      receipt photos to MinIO through presigned URLs, thumbnail job
├── analytics-service/   the read side: the dashboard fan-out, and the insights read model rebuilt by replaying the topic
├── jwt-lab/             a hand-rolled JWT and the alg=none / key-confusion attacks (nothing may depend on it)
├── architecture-tests/  cross-module ArchUnit rules: service isolation, outbound HTTP, database ownership
├── docs/adr/            architecture decision records (docs/ also holds the outbox and service-split write-ups)
├── frontend/            Angular: Material, SignalStore, generated API client, boundaries lint, Playwright
├── infra/               Postgres init, observability config
├── Dockerfile           every Spring service (SERVICE build arg); frontend/Dockerfile for the SPA
└── docker-compose.yml   the whole local stack, application included
```

## Running it

### Everything in Docker

Prerequisites: Docker only. The first run builds the service and frontend images from
source (a few minutes: Maven downloads the reactor's dependencies, `npm ci` the frontend's).

```bash
cp .env.example .env         # adjust ports if something already uses 5432 etc.
docker compose up -d         # infrastructure + user/account/transaction services + frontend
docker compose ps            # every row healthy (minio-init exits 0 by design, which is
                             # also why `up --wait` reports 1 — its job is done)
open http://localhost:4200   # sign in as dev@spendly.local / dev
```

After a code change: `docker compose up -d --build` (only the changed image is rebuilt; the
Maven repository and Boot's dependency layer stay cached).

Images are `spendly/<service>:local`, built by the root `Dockerfile` (one file, a `SERVICE`
build arg picks the module) and `frontend/Dockerfile` (nginx serving the production bundle).

### A service from the IDE

Prerequisites: Docker, JDK 25, Node 26 / npm 11. Free the port first, then run the service on
the host; it uses the same host-facing URLs as the container did.

```bash
docker compose up -d
docker compose stop user-service
set -a && . ./.env && set +a # host-run services read their datasource from the environment
./mvnw -pl user-service spring-boot:run

docker compose stop frontend
cd frontend && npm install && npm start
```

| Service          | URL                            | Credentials             |
| ---------------- | ------------------------------ | ----------------------- |
| Angular app      | http://localhost:4200          | `dev@spendly.local`/`dev`, `admin@gmail.com`/`admin`, or "Create account" |
| user-service     | http://localhost:8082          | bearer token; Swagger UI `/swagger-ui.html`; actuator `127.0.0.1:9091` |
| account-service  | http://localhost:8083          | bearer token; Swagger UI `/swagger-ui.html`; actuator `127.0.0.1:9083` |
| transaction-service | http://localhost:8084       | bearer token; Swagger UI `/swagger-ui.html`; actuator `127.0.0.1:9084` |
| budget-service   | http://localhost:8085          | bearer token; Swagger UI `/swagger-ui.html`; actuator `127.0.0.1:9085` |
| goal-service     | http://localhost:8086          | bearer token; Swagger UI `/swagger-ui.html`; actuator `127.0.0.1:9086` |
| recurring-service | http://localhost:8087         | bearer token; Swagger UI `/swagger-ui.html`; actuator `127.0.0.1:9087` |
| import-service   | http://localhost:8088          | bearer token; Swagger UI `/swagger-ui.html`; actuator `127.0.0.1:9088` |
| analytics-service | http://localhost:8090         | bearer token; Swagger UI `/swagger-ui.html`; actuator `127.0.0.1:9089` |
| notification-service | http://localhost:8091      | bearer token; Swagger UI `/swagger-ui.html`; actuator `127.0.0.1:9191` |
| auth-service     | http://localhost:8096          | sign-in API and JWKS; Swagger UI `/swagger-ui.html`; actuator `127.0.0.1:9096` |
| MinIO console    | http://localhost:9001          | from `.env`             |
| Mailpit          | http://localhost:8025          | –                       |
| Kafka UI         | http://localhost:8099          | –                       |
| Schema Registry  | http://localhost:8094          | –; the committed event schemas, BACKWARD |
| Debezium Connect | http://localhost:8095          | only with `--profile cdc` |
| Grafana          | http://localhost:3000          | `admin`/`admin`         |

Two realm users are seeded: `dev@spendly.local`/`dev` (USER) and
`admin@gmail.com`/`admin` (USER + ADMIN). Anyone else creates their own account from the
login page ("Create account") with just an email and a password; new accounts get the USER
role. Their ids are pinned in the realm JSON so a re-import never changes a user's subject
and orphans their profile row in `user-service`.

### Debezium instead of the outbox poller

By default each service ships its own `outbox_event` rows to Kafka. To let Debezium do it:

```bash
SPENDLY_OUTBOX_MODE=cdc docker compose --profile cdc up -d
```

`cdc` without `--profile cdc` leaves nothing publishing, so events stop flowing. The comparison
and its measurements are in `docs/outbox-poller-vs-debezium.md`.

### Port conflicts

Every host port is an env var with a conventional default. If another project already
holds one, override it in `.env` — `POSTGRES_PORT=5433` and so on. The application ports
(`FRONTEND_PORT`, `USER_SERVICE_PORT`, …) are also baked into the frontend bundle's `environment.ts`
(and `SPENDLY_PUBLIC_URL` / `SPENDLY_FRONTEND_URL` for Google's redirect), so moving those means changing both.

### How the services trust a token

`auth-service` signs every access token with `iss` set to `SPENDLY_OIDC_ISSUER` (`http://localhost:8096`
by default) and publishes its public keys at `/.well-known/jwks.json`. Every other service validates the
signature, the issuer and the `spendly-api` audience; inside compose they fetch the keys from
`SPENDLY_OIDC_JWK_SET_URI=http://auth-service:8096/.well-known/jwks.json`, on the host from the default
address. Nothing uses OIDC discovery, which `auth-service` does not serve.

## Authentication

Sign-in belongs to `auth-service` (CLAUDE.md §6.32, ADR-0007):

- **Email and password.** `POST /api/v1/auth/register` and `/login`. Passwords are bcrypt-hashed; ten wrong
  attempts lock the account for a minute.
- **Two-step verification (optional, per person).** Turn it on in *Settings → Two-step verification*: scan
  the QR code with an authenticator app such as Google Authenticator, enter the code, and save the eight
  recovery codes shown once. From then on a password sign-in asks for a code.
- **Google.** The button appears once Google is configured (below).
- **The session** is a short-lived access token held in the browser's memory and a single-use refresh
  token in an `HttpOnly` cookie, so a reload keeps you signed in and nothing is stored in `localStorage`.
- **Money-moving actions** (a transfer, a reversal) ask for your password again if you signed in more than
  fifteen minutes ago.

The demo users `dev@spendly.local` / `dev` and `admin@gmail.com` / `admin` exist wherever demo data is on.

`user-service` is a pure resource server like the rest: a local `users` row is created just-in-time on the
first authenticated request, from the token's claims.

### Enabling Google sign-in

`auth-service` runs the authorization-code flow itself, so it needs credentials but no other set-up.

1. In the [Google Cloud console](https://console.cloud.google.com/apis/credentials) create an
   **OAuth 2.0 Client ID** of type *Web application* (the consent screen can stay in *Testing*; add the
   Google accounts that will sign in as test users).
2. Add this authorised redirect URI — exactly, with no trailing slash:
   `http://localhost:8081/api/v1/auth/google/callback`
3. Put the id and secret in `.env` as `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.
4. Recreate the service so it picks them up:
   ```bash
   docker compose up -d auth-service
   ```

With both values empty the Google button is simply not shown. A person who already registered with a
password and then signs in with Google gets the same account; because that address was never verified,
its password and second factor are discarded at that moment.

## Testing

### The running stack

With `docker compose up -d` done:

```bash
docker compose ps                                   # health of every container
curl -s localhost:9091/actuator/health | jq .status # user-service (9083, 9084 likewise)
open http://localhost:8083/swagger-ui.html          # "Authorize" with a bearer token
cd frontend && npm run e2e                          # Playwright drives the real login through
                                                    # the frontend container on :4200
docker compose logs -f user-service                 # plain-text logs, correlation id per line
```

To call an API by hand, sign in at http://localhost:4200, copy the `Authorization` header
from the dashboard's `/api/v1/users/me` request in the browser dev tools, and reuse it:

```bash
curl -s -H "Authorization: Bearer $TOKEN" localhost:8083/api/v1/accounts | jq
```

### The build gate

```bash
./mvnw verify                # the gate: Spotless, Error Prone + NullAway, SpotBugs, Modernizer,
                             # enforcer, JaCoCo report, ArchUnit, Testcontainers (needs Docker)
./mvnw spotless:apply        # before verify, so it fails on defects rather than formatting
cd frontend
npm run lint                 # angular-eslint + boundaries
npm run e2e                  # Playwright; login specs skip themselves if the stack is down
```

No unit tests are written in this project (see `CLAUDE.md` §5.4.1); integration tests over
the real stack carry the weight. `ConcurrentProvisioningTest` is the shape to copy.


## Demo data

`dev@spendly.local` is seeded with accounts, a few months of ledger entries, budgets and
recurring rules, so every screen has something on it the first time you open the app. It is
defined once in `common.demo.DemoData` and written by each service's own
`demo.TestDataGenerator`; seeding is idempotent and never runs in a deployed profile.

Shape it from `.env` (or the environment) and restart:

```bash
SPENDLY_DEMO_DATA=false        # none at all
SPENDLY_DEMO_DATA_MONTHS=12    # a year of history instead of four months
SPENDLY_DEMO_DATA_SCALE=250    # every amount x2.5
SPENDLY_DEMO_DATA_RESET=true   # wipe the demo rows and rebuild them
```

After changing `MONTHS` or `SCALE`, run once with `RESET=true` — otherwise the old rows stay
alongside the new ones. Adding an entity? Extend `DemoData` and that service's
`TestDataGenerator` in the same change (`CLAUDE.md` §6.22).

## What the app does today

Sign in and you get five screens, all composed from Angular Material:

| Screen | What works |
| --- | --- |
| **Overview** | Balances per currency, month-to-date spend against budget, what is due in 30 days, budgets needing attention, recent activity |
| **Accounts** | Open cash/card/bank/savings/investment accounts, per-currency totals, archive, reconcile against the ledger |
| **Transactions** | Keyset-paginated feed grouped by day, full-text search, filters by kind and account, add income/expense/transfer with an optional receipt photo, reverse an entry |
| **Budgets** | A month's limits per category with progress, projection and 50/80/100% alerts; optionally roll what is left unspent into next month; copy last month; unbudgeted-spending panel |
| **Recurring** | Repeat rules with pause/resume, the next-30-days calendar, and a scheduler that posts due entries into the ledger |
| **Categories** | The tree per owner, create/rename/archive, and merge one category into another (every entry and split is relabelled) |
| **Transaction detail** | One entry with its splits, receipts and links to its reversal; relabel the category and notes, or append a reversing entry |
| **Dead letters** *(administrators)* | Events a service gave up on after its retries, with the reason each failed, and a button to replay one |

Spend reaches the budgets through Kafka, not a shared table: `transaction-service` publishes
from its outbox, `budget-service` projects the allocations, and a threshold crossing is its
own event. `recurring-service` works the same way in reverse — it asks the ledger to post,
then settles the occurrence when the ledger answers.

`account-service` follows the same stream: each entry becomes a posting, so a balance is the sum
of the ledger and reconciles against it. An event that arrives before the row it needs is retried
on 5 s and 60 s topics, and one that still fails ends up in a dead-letter topic an administrator can
read and replay from the screen above. See `CLAUDE.md` §6.16 and §6.18.

The ledger's `TransactionCreated` now reaches six services, and none of them waits on another
(ADR-0005 says why that is choreography and what would not be):

- **Goals** (`goal-service`): a goal's round-ups come from the same events (spare change up to the next whole
  euro, five or ten), a monthly contribution is added on its day, and each goal answers "at this pace, when?" and
  "is that affordable?" from the owner's recent surplus. Goals are earmarks: they move no money in the ledger.
- **Insights** (`analytics-service`): spending by month, the biggest category changes, charges that repeat every
  month, where the month is heading and unusual categories, all from a read model of its own. An administrator can
  wipe it and rebuild it from the topic (`POST /api/v1/admin/analytics-service/rebuild`); the insights say
  "refreshing" meanwhile.
- **Detected charges** (`recurring-service`): a Kafka Streams topology notices a merchant charging the same amount
  on a rhythm, and the person can turn it into a recurring rule or dismiss it.
- **Notifications** (`notification-service`): budget thresholds, new recurring charges, reached goals, unusual
  spending and a Monday summary reach the inbox, email (Mailpit at <http://localhost:8025>) and web push, in the
  reader's language, subject to per-kind preferences and quiet hours. The browser side of web push (a service
  worker) is not built, so push is delivered only to a stub in the tests (`CLAUDE.md` §13).

The three new databases are created on the first start of an empty Postgres volume. A stack that already existed
needs them added once: `for db in goal notification analytics; do docker compose exec postgres psql -U spendly -c
"CREATE DATABASE spendly_$db"; done`, or `docker compose down -v`.

## Conventions

- **Money is never a `double`.** Use `com.spendly.common.money.Money`, which is
  `BigDecimal`-backed, normalised to the currency's scale, and refuses cross-currency
  arithmetic.
- **Flyway owns the schema.** Hibernate runs with `ddl-auto=validate`, so a mapping that drifts
  from a migration fails at startup rather than in production.
- **Ids are UUIDv7.** Time-ordered, so inserts stay at the right edge of the primary-key index.
  Hibernate's own `Style.TIME` is a version-1 variant that embeds the host IP, so
  `common`'s `UuidV7Generator` is used instead.
- **`events` stays framework-free.** `ContractPurityTest` fails the build if a Spring, Jakarta,
  Jackson or Kafka type appears in it.
