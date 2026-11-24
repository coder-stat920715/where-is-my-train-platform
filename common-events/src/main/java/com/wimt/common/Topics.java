package com.wimt.common;

import java.time.LocalDate;

/** Kafka topic names shared by all services. */
public final class Topics {
    private Topics() {}

    // Produced by Debezium EventRouter from the outbox tables
    public static final String OUTBOX_TELEMETRY_RECEIVED = "outbox.event.TelemetryReceived";
    public static final String OUTBOX_ETA_RECALCULATED = "outbox.event.EtaRecalculated";
    public static final String OUTBOX_ETA_FAILED = "outbox.event.EtaFailed";
    public static final String OUTBOX_ETA_REVERTED = "outbox.event.EtaReverted";

    // Saga command / reply channels
    public static final String SAGA_ETA_COMMANDS = "saga.eta.commands";
    public static final String SAGA_BOARD_COMMANDS = "saga.board.commands";
    public static final String SAGA_TELEMETRY_COMMANDS = "saga.telemetry.commands";
    public static final String SAGA_REPLIES = "saga.replies";

    /** Partition key: all events of one train journey stay ordered on one partition. */
    public static String trainKey(String trainNumber, LocalDate journeyDate) {
        return trainNumber + ":" + journeyDate;
    }
}
