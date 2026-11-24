-- Persistent saga state machine
CREATE TABLE saga_instances (
    id               UUID         PRIMARY KEY,
    trigger_event_id UUID         NOT NULL UNIQUE,       -- TelemetryReceived eventId => idempotent saga start
    train_number     VARCHAR(8)   NOT NULL,
    journey_date     DATE         NOT NULL,
    ping_id          BIGINT       NOT NULL,
    state            VARCHAR(20)  NOT NULL,              -- STARTED|ETA_CALCULATED|BOARD_UPDATED|COMPLETED|COMPENSATING|FAILED
    projection_id    BIGINT,
    failure_reason   VARCHAR(500),
    history          TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_saga_state_updated ON saga_instances (state, updated_at);
CREATE INDEX idx_saga_train ON saga_instances (train_number, journey_date, created_at DESC);
