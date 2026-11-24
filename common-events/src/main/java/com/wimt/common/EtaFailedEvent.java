package com.wimt.common;

import java.time.LocalDate;
import java.util.UUID;

/** Outbox event type "EtaFailed": telemetry rejected by domain invariants (anomaly). */
public record EtaFailedEvent(
        UUID eventId,
        UUID sagaId,
        long pingId,
        String trainNumber,
        LocalDate journeyDate,
        String reason) {
}
