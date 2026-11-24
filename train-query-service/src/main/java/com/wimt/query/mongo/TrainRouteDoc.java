package com.wimt.query.mongo;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Denormalized route document used by station-to-station search (collection train_routes). */
@Document("train_routes")
public record TrainRouteDoc(
        @Id String trainNumber,
        String trainName,
        String trainType,
        List<String> runsOn,
        List<StopDoc> stops) {

    public record StopDoc(int seq, String code, String name, String platform, String arr, String dep,
                          int dayOffset, double distanceKm) {}
}
