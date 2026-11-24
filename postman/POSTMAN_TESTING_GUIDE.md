# Postman Testing Guide - Where Is My Train Platform

Files in `postman/`:
* `WhereIsMyTrain.postman_collection.json` - 64 requests in 10 folders, each with a description (purpose, prerequisites, steps, expected result) and automated tests
* `WhereIsMyTrain.local.postman_environment.json` - base URLs for all services and an API key

## 1. Prepare
1. `docker compose up -d` and `./infra/debezium/register-connectors.sh`
2. Build (`mvn clean install -DskipTests`) and start the 6 services in the order from the README.
   Wait ~10 s after the ingestion service starts: the simulator begins emitting pings for train 22895.
3. Postman: **Import** both JSON files, select environment **Where Is My Train - Local**.
4. `journeyDate` (today, IST) and all timestamps (e.g. `t_dep_hwh` = 14:55 IST) are computed by the collection pre-request script.
   To use another date set the collection variable `journeyDateOverride` (e.g. `2026-11-16`).

## 2. Step-by-step test plan

### Step 1 - Infrastructure health (folder 00)
Run the folder. Expect: 5 services registered in Eureka, 5 actuator health = UP, both Debezium connectors/tasks RUNNING.
Failure hints: connector FAILED -> Postgres not started with `wal_level=logical` or databases missing; service missing in Eureka -> not started / wrong port.

### Step 2 - Simulator + live reads (folders 01 and 06)
1. 01 > *Simulator - status*: `running:true`, `currentStation` advancing.
2. 06 > *Running status 22895*: 200, status RUNNING, 6 stops. Re-send every ~5 s; `lastCrossedStation`, position and `delayMinutes` change.
   Expected journey: starts ~4 min late; delay drifts with section jitter and is partially recovered downstream (6 % padding, halts shrink to 2 min).
3. 06 > *Station-to-station HWH -> BBS*: both 22895 and 12841; 22895 has `live:true`.
4. 06 > *Live board HWH / KGP / BBS*: 22895 appears with platforms 8 / 3 / 2; `status` moves EXPECTED -> AT_PLATFORM -> DEPARTED as the train passes.
5. 06 > *Board HWH at fixed time*, *Delay history* (MongoDB) and negative tests (404 unknown train, empty reverse search).

### Step 3 - Saga pipeline (folder 03)
Run *Saga stats*: `COMPLETED` grows every few seconds, `STARTED/ETA_CALCULATED` stay near 0. *Completed sagas*: `history` shows
`STARTED | ETA_CALCULATED | BOARD_UPDATED | COMPLETED` with timestamps.

### Step 4 - Manual telemetry, validation, idempotency (folder 02, train 12841)
Run requests 1-5 in order (Runner delay 2500 ms). They create a valid journey: depart HWH (5 min late) -> GPS -> cell tower -> arrive KGP -> depart KGP.
* 6 duplicate -> HTTP 200 `DUPLICATE` (no second outbox event).
* 7-12 validation -> 422 `INVALID_GPS`, `INVALID_SPEED`, `STATION_REQUIRED`, `UNKNOWN_STATION`, `UNKNOWN_TRAIN`; 400 `VALIDATION_FAILED`.
* Then 06 > *Running status 12841*: HWH and KGP crossed, next = BLS, HWH departure delay about 5 min.

### Step 5 - Saga compensation (folders 02/13-14, 04, 03)
1. 02 > 13 (jump to MAS) and 14 (backward to HWH) return 202 (ingestion accepts; ETA engine rejects).
2. Wait 2-3 s, then 03 > *FAILED sagas*: failureReason `IMPOSSIBLE_STATION_JUMP` / `BACKWARD_STATION_MOVE`, history `COMPENSATING -> FAILED`.
3. 02 > 15 *Recent pings*: those pings now have `anomaly:true`.
4. 06 > *Running status 12841* is unchanged (bad data never reached the read model).
5. Same for the simulator: 04 > *Inject anomaly* (22895) while it runs; live view stays correct.

### Step 6 - ETA engine diagnostics (folder 05)
State/projection for each train (404 means no telemetry yet for that date).

### Step 7 - Real-time push (folder 07)
Open http://localhost:8080/live.html or :8084, Connect: a STOMP message arrives per ping. SSE: send *SSE stream* and leave it open.

### Step 8 - Gateway behaviour (folder 08)
* Rate limit: run *reset counters*, then Collection Runner on *burst target* only (100 iterations, delay 0). ~40 requests pass, then 429s; `X-RateLimit-Remaining/Burst-Capacity=40/Replenish-Rate=20` headers on all.
* *Separate bucket per API key* returns 200 immediately after the burst.
* Fallback drill: warm the cache with a board GET, stop `train-query-service`, repeat the same URL -> 200 with `X-Degraded: cached-response`; a new URL -> 503 `X-Degraded: no-cache`. Stop ingestion -> POST telemetry returns 503 + `Retry-After: 10`.

### Step 9 - Resilience4j (folder 09)
`/actuator/circuitbreakers` on the query service. Stop Redis (`docker compose stop redis`), call *Running status* repeatedly: stale or 503, breaker `redisRead` opens; start Redis and it recovers.

## 3. Collection Runner / Newman
```bash
newman run postman/WhereIsMyTrain.postman_collection.json -e postman/WhereIsMyTrain.local.postman_environment.json \
  --folder "00 - Infrastructure Health" --folder "06 - Query Side (CQRS reads via Gateway)" --delay-request 1500
newman run ... --folder "02 - Telemetry Ingestion (CQRS command side)" --delay-request 2500
```

## 4. Replay / reset
A train journey can finish only once per date (later pings would be duplicates or backward moves). To start over:
```bash
./infra/reset-demo.sh          # wipes pings, ETA state, sagas, Redis views, Mongo history
# restart train-query-service, then:
curl -X POST localhost:8080/api/v1/simulator/start
```
Or set `journeyDateOverride` to another date within +-2 days for manual telemetry.

## 5. Troubleshooting
| Symptom | Cause / fix |
|---|---|
| 404 on running status | no telemetry yet; wait for simulator or check `01 > status` |
| Sagas stuck in STARTED | Debezium connector not RUNNING (folder 00) or ETA engine down |
| Sagas stuck in ETA_CALCULATED then FAILED | query service/Redis down (saga reverts the ETA projection) |
| Simulator pings all rejected as DUPLICATE | state from an earlier run; run `reset-demo.sh` |
| 429 on normal requests | gateway rate limiter (20 req/s, burst 40); slow the runner or use a different `X-API-Key` |
| Boards empty | restart query service (re-bootstraps boards) or pass `asOf` |
