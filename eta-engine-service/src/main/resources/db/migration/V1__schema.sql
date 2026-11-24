-- Domain reference data + ETA engine command-side tables
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

CREATE TABLE train_state (
    id               BIGSERIAL PRIMARY KEY,
    train_number     VARCHAR(8)  NOT NULL,
    journey_date     DATE        NOT NULL,
    last_seq         INT         NOT NULL DEFAULT -1,
    departed         BOOLEAN     NOT NULL DEFAULT FALSE,
    delay_minutes    INT         NOT NULL DEFAULT 0,
    latitude         DOUBLE PRECISION,
    longitude        DOUBLE PRECISION,
    speed_kmph       DOUBLE PRECISION,
    last_event_at    TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (train_number, journey_date)
);

CREATE TABLE stop_actuals (
    id           BIGSERIAL PRIMARY KEY,
    train_number VARCHAR(8) NOT NULL,
    journey_date DATE       NOT NULL,
    seq          INT        NOT NULL,
    actual_arr   TIMESTAMPTZ,
    actual_dep   TIMESTAMPTZ,
    UNIQUE (train_number, journey_date, seq)
);

-- One row per ETA recalculation, holding the previous state so the saga can compensate (revert)
CREATE TABLE eta_projections (
    id                  BIGSERIAL PRIMARY KEY,
    saga_id             UUID        NOT NULL,
    train_number        VARCHAR(8)  NOT NULL,
    journey_date        DATE        NOT NULL,
    delay_minutes       INT         NOT NULL,
    reverted            BOOLEAN     NOT NULL DEFAULT FALSE,
    prev_last_seq       INT         NOT NULL,
    prev_departed       BOOLEAN     NOT NULL,
    prev_delay_minutes  INT         NOT NULL,
    prev_latitude       DOUBLE PRECISION,
    prev_longitude      DOUBLE PRECISION,
    prev_speed_kmph     DOUBLE PRECISION,
    prev_last_event_at  TIMESTAMPTZ,
    touched_seq         INT,
    had_actual_row      BOOLEAN     NOT NULL DEFAULT FALSE,
    prev_actual_arr     TIMESTAMPTZ,
    prev_actual_dep     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_eta_proj_train ON eta_projections (train_number, journey_date, id DESC);

-- Idempotent command handling
CREATE TABLE processed_events (
    event_id     UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
    id            UUID         PRIMARY KEY,
    aggregatetype VARCHAR(100) NOT NULL,
    aggregateid   VARCHAR(100) NOT NULL,
    type          VARCHAR(100) NOT NULL,
    payload       JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
