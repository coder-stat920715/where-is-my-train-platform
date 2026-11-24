package com.wimt.query.model;

import com.wimt.common.StopProjection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Message pushed over WebSocket / SSE: current headline values plus only the stops that changed. */
public record LiveDiff(
        String type,
        String trainNumber,
        LocalDate journeyDate,
        String status,
        int delayMinutes,
        Integer previousDelayMinutes,
        String lastCrossedStation,
        String nextStation,
        Double latitude,
        Double longitude,
        Double speedKmph,
        Instant destinationEta,
        Instant recordedAt,
        List<StopProjection> changedStops) {
}
