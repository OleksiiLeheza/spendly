# Spendly

Personal finance platform: expenses, budgets, goals and shared/split expenses.
Java 25 + Spring Boot 4 on the back end, Angular 22 + Angular Material on the front,
Keycloak for identity.

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
├── docs/adr/            architecture decision records
├── frontend/            Angular: Material, SignalStore, generated API client, boundaries lint, Playwright
├── infra/               Postgres init, Keycloak realm-as-code, observability config
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
| Keycloak console | http://localhost:8080          | `admin`/`admin`         |
| MinIO console    | http://localhost:9001          | from `.env`             |
| Mailpit          | http://localhost:8025          | –                       |
| Kafka UI         | http://localhost:8099          | –                       |
| Grafana          | http://localhost:3000          | `admin`/`admin`         |

Two realm users are seeded: `dev@spendly.local`/`dev` (USER) and
`admin@gmail.com`/`admin` (USER + ADMIN). Anyone else creates their own account from the
login page ("Create account") with just an email and a password; new accounts get the USER
role. Their ids are pinned in the realm JSON so a re-import never changes a user's subject
and orphans their profile row in `user-service`.

### Port conflicts

Every host port is an env var with a conventional default. If another project already
holds one, override it in `.env` — `POSTGRES_PORT=5433` and so on. The application ports
(`FRONTEND_PORT`, `USER_SERVICE_PORT`, …) are also baked into the Keycloak realm's redirect
URIs and the frontend bundle's `environment.ts`, so moving those means changing both.

### How the containers reach Keycloak

The browser gets its tokens from `http://localhost:8080`, and `KC_HOSTNAME` pins that as the
issuer whatever hostname a request arrives on. A service container cannot reach
`localhost:8080`, so it is given `SPENDLY_OIDC_JWK_SET_URI=http://keycloak:8080/...` for the
signing keys while still validating the `iss` claim against `SPENDLY_OIDC_ISSUER`. On the
host the variable is unset and plain issuer discovery applies.

## Authentication

The browser never sees a client secret. `spendly-web` is a **public** Keycloak client using
Authorization Code + PKCE (S256); `user-service` is a pure resource server that validates the
resulting JWT and checks the `spendly-api` audience.

There is no login, signup or password-reset endpoint in the Java code — Keycloak owns
credentials, social login and account recovery. A local `users` row is created just-in-time on
the first authenticated request, from the token's claims.

Self-registration is Keycloak's own: "Create account" starts the same Authorization Code +
PKCE flow with `prompt=create`, which opens the realm's sign-up form. The realm's user
profile (`components` in `realm-spendly.json`) keeps that form to email + password — first
and last name are admin-only attributes, because Spendly keeps its own profile names in
`user-service` and never reads Keycloak's.

### Applying a realm change

`--import-realm` only imports into an empty database, so editing `realm-spendly.json` does
not touch a running Keycloak. Either recreate everything (`docker compose down -v`, which
also drops the services' data), or reset Keycloak alone:

```bash
docker compose stop keycloak
docker compose exec postgres psql -U spendly -d spendly \
  -c 'DROP DATABASE keycloak' -c 'CREATE DATABASE keycloak'
docker compose up -d keycloak
```

### Enabling Google sign-in

The realm already contains a Google identity provider; it just needs credentials.

1. Create an OAuth 2.0 Client ID in the
   [Google Cloud console](https://console.cloud.google.com/apis/credentials).
2. Add the authorised redirect URI:
   `http://localhost:8080/realms/spendly/broker/google/endpoint`
3. Put the id and secret in `.env` as `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.
4. Recreate the realm so the import picks them up:
   ```bash
   docker compose down -v && docker compose up -d
   ```

Until step 3, the "Continue with Google" button appears and correctly hands off to Google,
which then rejects the placeholder client id.

A GitHub provider is present but disabled, as a template for adding more.

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
