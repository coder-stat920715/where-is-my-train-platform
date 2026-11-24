-- Domain reference data + command-side tables
CREATE TABLE stations (
    code        VARCHAR(8)       PRIMARY KEY,
    name        VARCHAR(100)     NOT NULL,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL
);

CREATE TABLE trains (
    number          VARCHAR(8)   PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    train_type      VARCHAR(30)  NOT NULL,
    origin_code     VARCHAR(8)   NOT NULL REFERENCES stations(code),
    destination_code VARCHAR(8)  NOT NULL REFERENCES stations(code),
    runs_on         VARCHAR(40)  NOT NULL,
    max_speed_kmph  INT          NOT NULL
);

CREATE TABLE train_schedule (
    id           BIGSERIAL PRIMARY KEY,
    train_number VARCHAR(8)  NOT NULL REFERENCES trains(number),
    seq          INT         NOT NULL,
    station_code VARCHAR(8)  NOT NULL REFERENCES stations(code),
    sch_arr      TIME,
    sch_dep      TIME,
    day_offset   INT         NOT NULL DEFAULT 0,
    platform     VARCHAR(4),
    distance_km  DOUBLE PRECISION NOT NULL,
    UNIQUE (train_number, seq)
);

CREATE TABLE telemetry_pings (
    id             BIGSERIAL PRIMARY KEY,
    train_number   VARCHAR(8)        NOT NULL REFERENCES trains(number),
    journey_date   DATE              NOT NULL,
    event_type     VARCHAR(24)       NOT NULL,
    station_code   VARCHAR(8),
    latitude       DOUBLE PRECISION  NOT NULL,
    longitude      DOUBLE PRECISION  NOT NULL,
    speed_kmph     DOUBLE PRECISION  NOT NULL,
    source         VARCHAR(24)       NOT NULL,
    device_id      VARCHAR(64),
    recorded_at    TIMESTAMPTZ       NOT NULL,
    dedupe_key     VARCHAR(255)      NOT NULL UNIQUE,
    anomaly        BOOLEAN           NOT NULL DEFAULT FALSE,
    anomaly_reason VARCHAR(255),
    created_at     TIMESTAMPTZ       NOT NULL DEFAULT now()
);
CREATE INDEX idx_pings_train_date ON telemetry_pings (train_number, journey_date, id DESC);

-- Transactional outbox (captured by Debezium EventRouter)
CREATE TABLE outbox_events (
    id            UUID         PRIMARY KEY,
    aggregatetype VARCHAR(100) NOT NULL,
    aggregateid   VARCHAR(100) NOT NULL,
    type          VARCHAR(100) NOT NULL,
    payload       JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
