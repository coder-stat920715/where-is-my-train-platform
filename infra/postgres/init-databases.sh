#!/bin/bash
set -e
for db in telemetry_db eta_db saga_db; do
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -c "CREATE DATABASE $db"
done
