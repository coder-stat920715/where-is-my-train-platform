package com.wimt.query.mongo;

import com.wimt.common.StopProjection;
import com.wimt.query.model.BoardEntry;
import com.wimt.query.model.TrainLiveView;
import com.wimt.query.store.ViewStore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * On startup: seeds MongoDB train_routes + indexes, and bootstraps SCHEDULED live views and station boards
 * in Redis for today's journeys so reads work before the first telemetry event arrives.
 */
@Component
public class RouteSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RouteSeeder.class);
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final MongoTemplate mongo;
    private final ViewStore store;

    public RouteSeeder(MongoTemplate mongo, ViewStore store) {
        this.mongo = mongo;
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (mongo.count(new org.springframework.data.mongodb.core.query.Query(), TrainRouteDoc.class) == 0) {
                mongo.insertAll(RouteSeedData.routes());
                log.info("Seeded train_routes");
            }
            mongo.indexOps(TrainRouteDoc.class).createIndex(new Index().on("stops.code", Sort.Direction.ASC));
            mongo.indexOps(DelayHistoryDoc.class).createIndex(
                    new Index().on("trainNumber", Sort.Direction.ASC).on("journeyDate", Sort.Direction.ASC)
                            .on("recordedAt", Sort.Direction.ASC));
        } catch (RuntimeException e) {
            log.warn("MongoDB seeding skipped: {}", e.toString());
        }
        try {
            bootstrapRedis(LocalDate.now(IST));
        } catch (RuntimeException e) {
            log.warn("Redis bootstrap skipped: {}", e.toString());
        }
    }

    void bootstrapRedis(LocalDate date) {
        String day = date.getDayOfWeek().name().substring(0, 3);
        for (TrainRouteDoc route : RouteSeedData.routes()) {
            if (!route.runsOn().contains(day)) {
                continue;
            }
            List<StopProjection> stops = new ArrayList<>();
            for (TrainRouteDoc.StopDoc s : route.stops()) {
                Instant arr = at(date, s.dayOffset(), s.arr() != null ? s.arr() : s.dep());
                Instant dep = at(date, s.dayOffset(), s.dep() != null ? s.dep() : s.arr());
                stops.add(new StopProjection(s.seq(), s.code(), s.name(), s.platform(), arr, dep, arr, dep,
                        null, null, 0, 0, false));
            }
            if (!store.liveExists(route.trainNumber(), date)) {
                String first = stops.get(0).stationCode();
                TrainLiveView view = new TrainLiveView(route.trainNumber(), route.trainName(), date, "NOT_STARTED",
                        null, first, null, null, null, 0, stops.get(stops.size() - 1).expectedArrival(),
                        Instant.now(), List.of(), stops, false);
                store.saveLive(view);
            }
            for (StopProjection s : stops) {
                store.putBoardIfAbsent(BoardEntry.of(route.trainNumber(), route.trainName(), date, stops, s));
            }
        }
        log.info("Bootstrapped scheduled live views and boards for {}", date);
    }

    private static Instant at(LocalDate date, int dayOffset, String hhmm) {
        return date.plusDays(dayOffset).atTime(LocalTime.parse(hhmm)).atZone(IST).toInstant();
    }
}
