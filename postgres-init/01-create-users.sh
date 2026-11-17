#!/bin/bash
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'payment_svc') THEN
    CREATE ROLE payment_svc LOGIN PASSWORD '${PAYMENT_DB_PASSWORD}';
  END IF;
END
\$\$;

SELECT 'CREATE DATABASE payment_db OWNER payment_svc'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'payment_db')\gexec
EOSQL