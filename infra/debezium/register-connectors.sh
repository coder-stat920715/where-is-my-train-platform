#!/bin/bash
# Registers (or re-registers) both Debezium outbox connectors on Kafka Connect.
set -e
CONNECT_URL="${CONNECT_URL:-http://localhost:8085}"
DIR="$(cd "$(dirname "$0")" && pwd)"
until curl -sf "$CONNECT_URL/connectors" > /dev/null; do echo "waiting for Kafka Connect..."; sleep 3; done
for name in telemetry-outbox-connector eta-outbox-connector; do
  echo "Registering $name"
  curl -s -X DELETE "$CONNECT_URL/connectors/$name" > /dev/null || true
  curl -s -X POST -H "Content-Type: application/json" --data @"$DIR/$name.json" "$CONNECT_URL/connectors" | head -c 400
  echo
done
curl -s "$CONNECT_URL/connectors?expand=status"
echo
