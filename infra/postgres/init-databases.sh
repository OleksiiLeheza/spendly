#!/bin/bash
# Postgres runs every *.sh here once, on first boot of an empty data volume.
#
# One logical database per service (CLAUDE.md 6.1) plus Keycloak's own. Each gets its
# own Flyway history, and no service can reach another's tables even by accident.
set -euo pipefail

create_database() {
    local name="$1"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
        CREATE DATABASE ${name};
        GRANT ALL PRIVILEGES ON DATABASE ${name} TO ${POSTGRES_USER};
SQL
    echo "created database: ${name}"
}

create_database keycloak

# Core domain services (CLAUDE.md 3). Added here as each service appears.
create_database spendly_user
create_database spendly_account
create_database spendly_transaction
