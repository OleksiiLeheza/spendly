-- Local user profiles, provisioned from Keycloak identities.
--
-- No password column: Keycloak owns credentials (CLAUDE.md 8).
-- Flyway owns this schema; Hibernate runs ddl-auto=validate and refuses to start on drift.

CREATE TABLE users (
    id                 UUID         NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,

    keycloak_id        UUID         NOT NULL,
    email              VARCHAR(320) NOT NULL,
    first_name         VARCHAR(100),
    last_name          VARCHAR(100),
    display_name       VARCHAR(200),
    avatar_url         VARCHAR(2048),
    locale             VARCHAR(35),
    zone_id            VARCHAR(64),
    default_currency   CHAR(3)      NOT NULL DEFAULT 'USD',
    identity_provider  VARCHAR(50),
    role               VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    created_by         UUID,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_modified_by   UUID,
    last_modified_at   TIMESTAMPTZ  NOT NULL,
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_role   CHECK (role   IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    CONSTRAINT ck_users_currency_is_iso4217 CHECK (default_currency ~ '^[A-Z]{3}$')
);

-- One live profile per Keycloak identity. This constraint is what makes just-in-time
-- provisioning safe: two concurrent first requests race, one inserts, the other takes the
-- unique violation and re-reads instead of creating a duplicate.
CREATE UNIQUE INDEX ux_users_keycloak_id ON users (keycloak_id) WHERE deleted_at IS NULL;

-- Case-insensitive: Alice@example.com and alice@example.com are one person. Partial, so a
-- soft-deleted profile releases its email for re-registration.
CREATE UNIQUE INDEX ux_users_email ON users (LOWER(email)) WHERE deleted_at IS NULL;

-- Soft delete is the default read path (CLAUDE.md 6.12), so the live set gets its own index.
CREATE INDEX ix_users_live ON users (id) WHERE deleted_at IS NULL;
