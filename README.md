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
│                        Money value object, UUIDv7 generator
├── events/              contract-only Kafka event schemas (no framework - enforced by ArchUnit)
├── user-service/        user profiles, Flyway migrations, OAuth2 resource server
├── web/                 Angular workspace (Material, ESLint, Prettier, Vitest, Playwright)
└── infra/               Postgres init, Keycloak realm-as-code
```

## Running it

Prerequisites: Docker, JDK 25, Node 22+.

```bash
cp .env.example .env         # adjust ports if something already uses 5432 etc.
docker compose up -d         # Postgres, Keycloak, Redis, MinIO, Mailpit
./mvnw install -DskipTests
./mvnw -pl user-service spring-boot:run

cd web && npm install && npm start
```

| Service          | URL                            | Credentials             |
| ---------------- | ------------------------------ | ----------------------- |
| Angular app      | http://localhost:4200          | `dev@spendly.local`/`dev` |
| user-service     | http://localhost:8081          | bearer token required   |
| Keycloak console | http://localhost:8080          | `admin`/`admin`         |
| MinIO console    | http://localhost:9001          | from `.env`             |
| Mailpit          | http://localhost:8025          | –                       |

Two realm users are seeded: `dev@spendly.local`/`dev` (USER) and
`admin@spendly.local`/`admin` (USER + ADMIN).

### Port conflicts

Every host port is an env var with a conventional default. If another project already
holds one, override it in `.env` — `POSTGRES_PORT=5433` and so on. `user-service` reads
the same variables, so the app and the container stay in step.

## Authentication

The browser never sees a client secret. `spendly-web` is a **public** Keycloak client using
Authorization Code + PKCE (S256); `user-service` is a pure resource server that validates the
resulting JWT and checks the `spendly-api` audience.

There is no login, signup or password-reset endpoint in the Java code — Keycloak owns
credentials, social login and account recovery. A local `users` row is created just-in-time on
the first authenticated request, from the token's claims.

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

```bash
./mvnw test                  # JUnit + ArchUnit + Testcontainers (needs Docker)
cd web
npm test                     # Vitest unit tests
npm run lint
npm run e2e                  # Playwright; needs the stack running for the login specs
```

The Playwright login specs skip themselves when Keycloak or user-service is unreachable, so a
machine that only wants unit tests is not forced to run Docker.

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
