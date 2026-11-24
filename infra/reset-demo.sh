#!/bin/bash
# Clears all runtime demo state (telemetry, ETA state, sagas, Redis views, Mongo history) so the simulator and
# manual Postman flows can be replayed on the same journeyDate. Reference data / schema are kept.
# Run from the repository root while `docker compose` infrastructure is up.
set -e
PSQL="docker compose exec -T postgres psql -U postgres -v ON_ERROR_STOP=1"
$PSQL -d telemetry_db -c "delete from telemetry_pings; delete from outbox_events;"
$PSQL -d eta_db -c "delete from stop_actuals; delete from eta_projections; delete from train_state; delete from processed_events; delete from outbox_events;"
$PSQL -d saga_db -c "delete from saga_instances;"
docker compose exec -T redis sh -c "for p in 'train:live:*' 'station:board:*' 'processed_events:*' 'platform:clock' 'gw:cache:*'; do redis-cli --scan --pattern \"\$p\" | xargs -r redis-cli del > /dev/null; done"
docker compose exec -T mongodb mongosh wimt --quiet --eval "db.delay_history.deleteMany({})"
echo "State cleared. Now RESTART train-query-service (re-bootstraps today's boards) and then:"
echo "  curl -X POST localhost:8080/api/v1/simulator/start"
