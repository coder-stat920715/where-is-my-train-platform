package com.wimt.common;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Outbox event type "TelemetryReceived" (topic outbox.event.TelemetryReceived). */
public record TelemetryReceivedEvent(
        UUID eventId,
        long pingId,
        String trainNumber,
        LocalDate journeyDate,
        String eventType,      // POSITION | STATION_ARRIVAL | STATION_DEPARTURE
        String stationCode,    // null for POSITION
        double latitude,
        double longitude,
        double speedKmph,
        String source,         // GPS | CELL_TOWER | STATION_CROSSING
        Instant recordedAt) {
}
