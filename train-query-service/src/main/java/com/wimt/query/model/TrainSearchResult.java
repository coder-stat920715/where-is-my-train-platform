package com.wimt.query.model;

import java.time.Instant;

public record TrainSearchResult(
        String trainNumber,
        String trainName,
        String fromStation,
        String toStation,
        Instant scheduledDeparture,
        Instant scheduledArrival,
        Instant expectedDeparture,
        Instant expectedArrival,
        int delayMinutes,
        String status,        // SCHEDULED | NOT_STARTED | RUNNING | COMPLETED
        long durationMinutes,
        boolean live,
        boolean stale) {
}
