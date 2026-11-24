package com.wimt.query.mongo;

import com.wimt.query.mongo.TrainRouteDoc.StopDoc;
import java.util.List;

/** Seed timetable (kept in sync with the Flyway seed of the command-side services). */
public final class RouteSeedData {
    private RouteSeedData() {}

    private static final List<String> DAILY = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    public static List<TrainRouteDoc> routes() {
        return List.of(
                new TrainRouteDoc("22895", "Puri Vande Bharat Exp", "VANDE_BHARAT", DAILY, List.of(
                        new StopDoc(0, "HWH", "Howrah Junction", "8", null, "06:10", 0, 0),
                        new StopDoc(1, "KGP", "Kharagpur Junction", "3", "07:25", "07:27", 0, 116),
                        new StopDoc(2, "BLS", "Balasore", "1", "08:50", "08:52", 0, 233),
                        new StopDoc(3, "CTC", "Cuttack Junction", "1", "10:15", "10:17", 0, 384),
                        new StopDoc(4, "BBS", "Bhubaneswar", "2", "10:55", "10:57", 0, 438),
                        new StopDoc(5, "PURI", "Puri", "1", "12:15", null, 0, 500))),
                new TrainRouteDoc("12841", "Coromandel Express", "SUPERFAST", DAILY, List.of(
                        new StopDoc(0, "HWH", "Howrah Junction", "24", null, "14:50", 0, 0),
                        new StopDoc(1, "KGP", "Kharagpur Junction", "2", "16:30", "16:35", 0, 116),
                        new StopDoc(2, "BLS", "Balasore", "2", "18:07", "18:09", 0, 233),
                        new StopDoc(3, "CTC", "Cuttack Junction", "3", "19:50", "19:55", 0, 384),
                        new StopDoc(4, "BBS", "Bhubaneswar", "1", "20:35", "20:40", 0, 438),
                        new StopDoc(5, "MAS", "Chennai Central", "5", "17:15", null, 1, 1662))));
    }
}
