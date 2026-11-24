package com.wimt.common;

import java.time.Instant;

/** One station of a train journey with scheduled, expected and actual times. */
public record StopProjection(
        int seq,
        String stationCode,
        String stationName,
        String platform,
        Instant scheduledArrival,
        Instant scheduledDeparture,
        Instant expectedArrival,
        Instant expectedDeparture,
        Instant actualArrival,
        Instant actualDeparture,
        int arrivalDelayMinutes,
        int departureDelayMinutes,
        boolean crossed) {
}
