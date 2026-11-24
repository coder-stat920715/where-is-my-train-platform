package com.wimt.common;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Outbox event type "EtaRecalculated"; also the payload of the board-update command. */
public record EtaRecalculatedEvent(
        UUID eventId,
        UUID sagaId,
        long projectionId,
        String trainNumber,
        String trainName,
        LocalDate journeyDate,
        String triggerType,        // telemetry event type that caused the recalculation
        String status,             // NOT_STARTED | RUNNING | COMPLETED
        String lastCrossedStation, // code or null
        String nextStation,        // code or null
        Double latitude,
        Double longitude,
        Double speedKmph,
        int delayMinutes,
        Instant recordedAt,
        Instant destinationEta,
        List<StopProjection> stops) {
}
