package com.wimt.query.model;

import com.wimt.common.EtaRecalculatedEvent;
import com.wimt.common.StopProjection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Denormalized "Where is my train" document stored in Redis at train:live:{trainNo}:{date}. */
public record TrainLiveView(
        String trainNumber,
        String trainName,
        LocalDate journeyDate,
        String status,
        String lastCrossedStation,
        String nextStation,
        Double latitude,
        Double longitude,
        Double speedKmph,
        int delayMinutes,
        Instant destinationEta,
        Instant lastUpdated,
        List<StopProjection> crossedStations,
        List<StopProjection> upcomingStations,
        boolean stale) {

    public static TrainLiveView from(EtaRecalculatedEvent e) {
        List<StopProjection> crossed = e.stops().stream().filter(StopProjection::crossed).toList();
        List<StopProjection> upcoming = e.stops().stream().filter(s -> !s.crossed()).toList();
        return new TrainLiveView(e.trainNumber(), e.trainName(), e.journeyDate(), e.status(), e.lastCrossedStation(),
                e.nextStation(), e.latitude(), e.longitude(), e.speedKmph(), e.delayMinutes(), e.destinationEta(),
                Instant.now(), crossed, upcoming, false);
    }

    public TrainLiveView asStale() {
        return new TrainLiveView(trainNumber, trainName, journeyDate, status, lastCrossedStation, nextStation, latitude,
                longitude, speedKmph, delayMinutes, destinationEta, lastUpdated, crossedStations, upcomingStations, true);
    }

    /** All stops of the journey in route order. */
    public List<StopProjection> stopsInOrder() {
        List<StopProjection> all = new ArrayList<>(crossedStations);
        all.addAll(upcomingStations);
        all.sort((a, b) -> Integer.compare(a.seq(), b.seq()));
        return all;
    }
}
