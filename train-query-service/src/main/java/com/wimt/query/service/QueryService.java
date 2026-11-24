package com.wimt.query.service;

import com.wimt.common.StopProjection;
import com.wimt.query.model.BoardEntry;
import com.wimt.query.model.ServiceDegradedException;
import com.wimt.query.model.StationBoard;
import com.wimt.query.model.TrainLiveView;
import com.wimt.query.model.TrainSearchResult;
import com.wimt.query.mongo.DelayHistoryDoc;
import com.wimt.query.mongo.RouteReadModel;
import com.wimt.query.mongo.TrainRouteDoc;
import com.wimt.query.mongo.TrainRouteDoc.StopDoc;
import com.wimt.query.store.LiveReadModel;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Read-only façade used by REST controllers. */
@Service
public class QueryService {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final LiveReadModel live;
    private final RouteReadModel routes;
    private final boolean useTelemetryClock;

    public QueryService(LiveReadModel live, RouteReadModel routes,
                        @Value("${query.board.use-telemetry-clock:true}") boolean useTelemetryClock) {
        this.live = live;
        this.routes = routes;
        this.useTelemetryClock = useTelemetryClock;
    }

    public Optional<TrainLiveView> runningStatus(String trainNumber, LocalDate date) {
        return live.getLive(trainNumber, date);
    }

    public List<TrainSearchResult> between(String from, String to, LocalDate date) {
        String f = from.toUpperCase();
        String t = to.toUpperCase();
        String day = date.getDayOfWeek().name().substring(0, 3);
        List<TrainSearchResult> results = new ArrayList<>();
        for (TrainRouteDoc route : routes.findRoutesBetween(f, t)) {
            StopDoc fromStop = find(route, f);
            StopDoc toStop = find(route, t);
            if (fromStop == null || toStop == null || toStop.seq() <= fromStop.seq() || !route.runsOn().contains(day)) {
                continue;
            }
            Instant schDep = at(date, fromStop.dayOffset(), fromStop.dep() != null ? fromStop.dep() : fromStop.arr());
            Instant schArr = at(date, toStop.dayOffset(), toStop.arr() != null ? toStop.arr() : toStop.dep());
            Instant expDep = schDep;
            Instant expArr = schArr;
            int delay = 0;
            String status = "SCHEDULED";
            boolean isLive = false;
            boolean stale = false;
            try {
                Optional<TrainLiveView> view = live.getLive(route.trainNumber(), date);
                if (view.isPresent()) {
                    TrainLiveView v = view.get();
                    stale = v.stale();
                    isLive = !"NOT_STARTED".equals(v.status());
                    status = v.status();
                    for (StopProjection s : v.stopsInOrder()) {
                        if (s.stationCode().equals(f)) {
                            expDep = s.expectedDeparture();
                        }
                        if (s.stationCode().equals(t)) {
                            expArr = s.expectedArrival();
                            delay = s.arrivalDelayMinutes();
                        }
                    }
                }
            } catch (ServiceDegradedException e) {
                stale = true;
            }
            results.add(new TrainSearchResult(route.trainNumber(), route.trainName(), fromStop.name(), toStop.name(),
                    schDep, schArr, expDep, expArr, delay, status, Duration.between(schDep, schArr).toMinutes(), isLive, stale));
        }
        results.sort(Comparator.comparing(TrainSearchResult::scheduledDeparture));
        return results;
    }

    public StationBoard board(String stationCode, int hours, Instant asOf) {
        String code = stationCode.toUpperCase();
        Instant now = Instant.now();
        Instant ref = asOf != null ? asOf
                : (useTelemetryClock ? live.platformClock().orElse(now) : now);
        Instant windowStart = ref.minus(Duration.ofMinutes(10));
        Instant windowEnd = ref.plus(Duration.ofHours(hours));
        List<BoardEntry> all = live.getBoard(code);
        List<BoardEntry> trains = all.stream()
                .filter(e -> e.boardTime() != null)
                .filter(e -> !e.boardTime().isAfter(windowEnd))
                .filter(e -> {
                    Instant leaves = e.expectedDeparture() != null ? e.expectedDeparture() : e.boardTime();
                    return !leaves.isBefore(windowStart);
                })
                .sorted(Comparator.comparing(BoardEntry::boardTime))
                .toList();
        String name = all.isEmpty() ? code : all.get(0).stationName();
        return new StationBoard(code, name, ref, hours, false, trains);
    }

    public List<DelayHistoryDoc> delayHistory(String trainNumber, LocalDate date) {
        return routes.delayHistory(trainNumber, date.toString());
    }

    private static StopDoc find(TrainRouteDoc route, String code) {
        return route.stops().stream().filter(s -> s.code().equals(code)).findFirst().orElse(null);
    }

    private static Instant at(LocalDate date, int dayOffset, String hhmm) {
        return date.plusDays(dayOffset).atTime(LocalTime.parse(hhmm)).atZone(IST).toInstant();
    }
}
