package com.wimt.ingestion.api;

import com.wimt.ingestion.service.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

/** Command: a GPS / cell-tower / station-crossing ping for a running train. */
public record TelemetryRequest(
        @NotBlank String trainNumber,
        @NotNull LocalDate journeyDate,
        @NotNull EventType eventType,
        String stationCode,
        @NotNull Double latitude,
        @NotNull Double longitude,
        Double speedKmph,
        String source,
        Instant recordedAt,
        String deviceId) {
}
