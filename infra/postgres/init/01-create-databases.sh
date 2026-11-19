#!/bin/bash
# =========================================================================
# PostgreSQL init: négy logikai DB + külön DB-user (D07).
# A postgres image ezt a scriptet a superuserrel futtatja az első induláskor.
# Minden service CSAK a saját DB-userével tud kapcsolódni; nincs
# cross-service DB-hozzáférés (3.4).
# =========================================================================
set -euo pipefail

create_db_and_user() {
  local db_name="$1"
  local db_user="$2"
  local db_pass="$3"

  echo "Creating database '${db_name}' and user '${db_user}'..."

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    CREATE USER ${db_user} WITH PASSWORD '${db_pass}';
    CREATE DATABASE ${db_name} OWNER ${db_user};
    REVOKE ALL ON DATABASE ${db_name} FROM PUBLIC;
    GRANT ALL PRIVILEGES ON DATABASE ${db_name} TO ${db_user};
EOSQL
}

create_db_and_user "${IDENTITY_DB_NAME}" "${IDENTITY_DB_USER}" "${IDENTITY_DB_PASSWORD}"
create_db_and_user "${LEARNING_DB_NAME}" "${LEARNING_DB_USER}" "${LEARNING_DB_PASSWORD}"
create_db_and_user "${SOCIAL_DB_NAME}"   "${SOCIAL_DB_USER}"   "${SOCIAL_DB_PASSWORD}"
create_db_and_user "${AI_DB_NAME}"       "${AI_DB_USER}"       "${AI_DB_PASSWORD}"

echo "All service databases created."
