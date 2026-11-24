package com.wimt.query.model;

import java.time.Instant;
import java.util.List;

public record StationBoard(String stationCode, String stationName, Instant asOf, int windowHours,
                           boolean stale, List<BoardEntry> trains) {
}
