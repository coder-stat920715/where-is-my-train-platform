package com.wimt.query.model;

import com.wimt.common.EtaRecalculatedEvent;
import com.wimt.common.StopProjection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One train on one station's departure board (field of the Redis hash station:board:{code}). */
public record BoardEntry(
        String trainNumber,
        String trainName,
        LocalDate journeyDate,
        String stationCode,
        String stationName,
        int seq,
        String platform,
        String destinationName,
        Instant scheduledArrival,
        Instant scheduledDeparture,
        Instant expectedArrival,
        Instant expectedDeparture,
        int delayMinutes,
        String status) {   // EXPECTED | AT_PLATFORM | DEPARTED

    public static BoardEntry of(String trainNumber, String trainName, LocalDate date, List<StopProjection> stops, StopProjection s) {
        String destination = stops.get(stops.size() - 1).stationName();
        String status = s.crossed() ? "DEPARTED" : (s.actualArrival() != null ? "AT_PLATFORM" : "EXPECTED");
        return new BoardEntry(trainNumber, trainName, date, s.stationCode(), s.stationName(), s.seq(), s.platform(),
                destination, s.scheduledArrival(), s.scheduledDeparture(), s.expectedArrival(), s.expectedDeparture(),
                s.arrivalDelayMinutes(), status);
    }

    public static BoardEntry from(EtaRecalculatedEvent e, StopProjection s) {
        return of(e.trainNumber(), e.trainName(), e.journeyDate(), e.stops(), s);
    }

    public String field() {
        return trainNumber + ":" + journeyDate;
    }

    /** The instant that decides where the train sits on the board. */
    public Instant boardTime() {
        return expectedArrival != null ? expectedArrival : expectedDeparture;
    }
}
