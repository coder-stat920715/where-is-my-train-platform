# Where Is My Train - Real-Time Railway Tracking Platform

Multi-module Spring Boot microservices platform: **Java 21 (virtual threads), Spring Boot 4.1.2, Spring Cloud 2025.1.4**,
CQRS, Transactional Outbox + Debezium CDC, orchestrated Saga, Redis + MongoDB read models, WebSocket/SSE push.
Design docs: [`docs/HLD.md`](docs/HLD.md), [`docs/LLD.md`](docs/LLD.md).

## Modules
| Module | Port | Purpose |
|---|---|---|
| `common-events` | - | shared event / command records, topics, JSON helper |
| `discovery-server` | 8761 | Eureka |
| `api-gateway` | 8080 | Gateway, `lb://`, Redis rate limiter, circuit breakers, fallbacks |
| `telemetry-ingestion-service` | 8081 | command API, **GPS simulator**, outbox |
| `train-saga-orchestrator` | 8082 | saga state machine |
| `eta-engine-service` | 8083 | delay/ETA engine, outbox |
| `train-query-service` | 8084 | Redis/Mongo read model, WebSocket, SSE |

## Prerequisites
JDK 21, Maven 3.9+, Docker. Maven must be able to download Spring Boot 4.1.2 / Spring Cloud 2025.1.4 artifacts.

## 1. Start infrastructure
```bash
docker compose up -d                       # Postgres(wal_level=logical), Kafka(KRaft), Debezium Connect, Redis, MongoDB, Zipkin
./infra/debezium/register-connectors.sh    # registers the two Outbox EventRouter connectors (once Connect is up)
```

## 2. Build
```bash
mvn clean install -DskipTests              # or: mvn test-compile
```

## 3. Start services (one terminal each, in this order)
```bash
mvn -pl discovery-server spring-boot:run
mvn -pl train-query-service spring-boot:run
mvn -pl eta-engine-service spring-boot:run
mvn -pl train-saga-orchestrator spring-boot:run
mvn -pl telemetry-ingestion-service spring-boot:run   # simulator auto-starts ~5 s after boot
mvn -pl api-gateway spring-boot:run
```
Eureka dashboard: http://localhost:8761 - Zipkin: http://localhost:9411 - Kafka Connect REST: http://localhost:8085 (host port 8085 because 8083 belongs to eta-engine-service).

## 4. Try it (through the gateway)
The simulator drives train **22895** HWH->KGP->BLS->CTC->BBS->PURI at 60x speed (6 h journey ~ 6 min).
```bash
TODAY=$(TZ=Asia/Kolkata date +%F)

# Live running status
curl -s "localhost:8080/api/v1/query/trains/22895/running-status?journeyDate=$TODAY" | jq

# Station to station (HWH -> BBS) with live delay
curl -s "localhost:8080/api/v1/query/trains/between?from=HWH&to=BBS&date=$TODAY" | jq

# Live station departure boards (next 4 hours)
curl -s "localhost:8080/api/v1/query/stations/HWH/board?hours=4" | jq
curl -s "localhost:8080/api/v1/query/stations/KGP/board" | jq
curl -s "localhost:8080/api/v1/query/stations/BBS/board" | jq

# Delay history (MongoDB)
curl -s "localhost:8080/api/v1/query/trains/22895/delay-history?journeyDate=$TODAY" | jq
```
> Station boards treat the **latest telemetry timestamp** as "now" (`query.board.use-telemetry-clock=true`), so the
> simulated journey is visible at any time of day. Pass `?asOf=2026-11-17T00:30:00Z` to pick an explicit time.

Simulator control & manual telemetry:
```bash
curl -s localhost:8080/api/v1/simulator/status | jq
curl -s -X POST localhost:8080/api/v1/simulator/stop
curl -s -X POST localhost:8080/api/v1/simulator/start

curl -s -X POST localhost:8080/api/v1/telemetry -H 'Content-Type: application/json' -d '{
  "trainNumber":"12841","journeyDate":"'$TODAY'","eventType":"STATION_DEPARTURE","stationCode":"HWH",
  "latitude":22.5839,"longitude":88.3425,"speedKmph":0,"source":"STATION_CROSSING","deviceId":"curl"}'
```

### Saga & compensation demo
```bash
curl -s localhost:8080/api/v1/sagas/stats | jq                       # COMPLETED count grows
curl -s -X POST localhost:8080/api/v1/simulator/inject-anomaly | jq   # impossible HWH->PURI jump
sleep 3
curl -s "localhost:8080/api/v1/sagas?state=FAILED" | jq '.[0] | {state,failureReason,history}'
curl -s "localhost:8080/api/v1/telemetry/recent?trainNumber=22895&journeyDate=$TODAY" | jq '.[] | select(.anomaly)'
```

### Verify the outbox / CDC
```bash
curl -s localhost:8085/connectors/telemetry-outbox-connector/status | jq
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 \
  --topic outbox.event.TelemetryReceived --from-beginning --max-messages 3
```

## 5. WebSocket (STOMP over SockJS) test
Browser: open **http://localhost:8084/live.html** (or `http://localhost:8080/live.html` via gateway), click *Connect*.
It subscribes to `/topic/trains/22895/<today>` and prints every live diff.

CLI alternative (SSE):
```bash
curl -N "localhost:8080/api/v1/stream/trains/22895/$TODAY"
```
JS snippet:
```js
const c = new StompJs.Client({ webSocketFactory: () => new SockJS('http://localhost:8084/ws') });
c.onConnect = () => c.subscribe('/topic/trains/22895/2026-11-17', m => console.log(JSON.parse(m.body)));
c.activate();
```

## 6. Resilience checks
```bash
# Rate limiter: 20 req/s steady, burst 40 -> some 429s
for i in $(seq 1 80); do curl -s -o /dev/null -w "%{http_code} " localhost:8080/api/v1/query/stations/HWH/board; done; echo
# Gateway fallback: stop train-query-service, repeat a previous GET -> cached body with X-Degraded header
curl -si "localhost:8080/api/v1/query/stations/HWH/board?hours=4" | head -20
curl -s localhost:8084/actuator/circuitbreakers | jq
```

## 7. Postman collection
`postman/` contains a full test suite (64 requests, automated tests) and `postman/POSTMAN_TESTING_GUIDE.md` with the step-by-step test plan. `infra/reset-demo.sh` resets demo state for replays.

## Notes
* Seed data (Flyway `V2__seed_data.sql`, Mongo `RouteSeedData`) uses illustrative timetables for `22895` and `12841`
  with stations `HWH, KGP, BLS, CTC, BBS, PURI` (+ `MAS` for the Coromandel Express).
* Services run on the host and connect to the compose infrastructure on localhost; Debezium reaches Postgres/Kafka by
  compose service name.
* Spring Boot 4 split the starters and moved to Jackson 3; Resilience4j annotations use `resilience4j-spring-boot3`
  (`${resilience4j.version}` in the parent POM). If your Resilience4j release ships a dedicated Boot 4 starter, swap
  the artifact in `train-query-service/pom.xml`.
