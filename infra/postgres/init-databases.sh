#!/bin/bash
# Postgres runs every *.sh in docker-entrypoint-initdb.d once, on first boot of an empty data
# volume, and the compose `databases` service runs this same file on every `up` so a database
# added for a new service also appears on a volume that predates it. Idempotent either way.
#
# One logical database per service (CLAUDE.md 6.1). Each gets its own Flyway history, and no
# service can reach another's tables even by accident.
set -euo pipefail

create_database() {
    local name="$1"
    local exists
    exists=$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -tAc \
        "SELECT 1 FROM pg_database WHERE datname = '${name}'")
    if [ "$exists" = "1" ]; then
        echo "database exists: ${name}"
        return
    fi
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
        CREATE DATABASE ${name};
        GRANT ALL PRIVILEGES ON DATABASE ${name} TO ${POSTGRES_USER};
SQL
    echo "created database: ${name}"
}

# Sign-in: credentials, sessions and signing keys.
create_database spendly_auth

# Core domain services (CLAUDE.md 3). Added here as each service appears.
create_database spendly_user
create_database spendly_account
create_database spendly_transaction

# Planning and data-in services (CLAUDE.md 2, P2/P3).
create_database spendly_budget
create_database spendly_recurring
create_database spendly_import

# The engagement tier: goals, notifications and the analytics read model.
create_database spendly_goal
create_database spendly_notification
create_database spendly_analytics

# Shared expenses: groups, invitations, the split engine and debt simplification (FI-53/54/55).
create_database spendly_sharing
