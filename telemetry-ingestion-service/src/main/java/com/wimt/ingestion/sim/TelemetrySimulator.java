package com.wimt.ingestion.sim;

import com.wimt.ingestion.api.TelemetryRequest;
import com.wimt.ingestion.api.TelemetryResponse;
import com.wimt.ingestion.domain.Repositories.StationRepository;
import com.wimt.ingestion.domain.Repositories.TrainScheduleRepository;
import com.wimt.ingestion.domain.Station;
import com.wimt.ingestion.domain.TrainSchedule;
import com.wimt.ingestion.service.EventType;
import com.wimt.ingestion.service.TelemetryService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background simulator emitting GPS / cell-tower / station-crossing pings for train 22895
 * along HWH -> KGP -> BLS -> CTC -> BBS -> PURI. Simulated time runs {step-seconds}x per tick
 * (default 60 s per second => the 6 h journey takes ~6 real minutes). Delay emerges naturally from
 * a 4-minute late departure plus random running-time jitter on every section.
 */
@Component
public class TelemetrySimulator {
    private static final Logger log = LoggerFactory.getLogger(TelemetrySimulator.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private enum Phase { WAITING, RUNNING }

    private record Stop(int seq, String code, double lat, double lon, Instant arr, Instant dep, double distKm) {}

    private static final class Run {
        LocalDate date;
        List<Stop> stops;
        int idx;
        Phase phase = Phase.WAITING;
        Instant clock;
        Instant departAt;
        double elapsed;
        double segTotal;
        long pings;
    }

    private final TelemetryService telemetry;
    private final TrainScheduleRepository schedules;
    private final StationRepository stations;

    @Value("${simulator.train-number:22895}")
    private String trainNumber;
    @Value("${simulator.step-seconds:60}")
    private int stepSeconds;
    @Value("${simulator.initial-delay-seconds:240}")
    private int initialDelaySeconds;
    @Value("${simulator.enabled:true}")
    private boolean autostart;

    private Run run;

    public TelemetrySimulator(TelemetryService telemetry, TrainScheduleRepository schedules, StationRepository stations) {
        this.telemetry = telemetry;
        this.schedules = schedules;
        this.stations = stations;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (autostart) {
            log.info(start());
        }
    }

    public synchronized String start() {
        List<TrainSchedule> rows = schedules.findByTrainNumberOrderBySeqAsc(trainNumber);
        if (rows.size() < 2) {
            return "No schedule found for train " + trainNumber;
        }
        Map<String, Station> byCode = new LinkedHashMap<>();
        stations.findAll().forEach(s -> byCode.put(s.getCode(), s));
        Run r = new Run();
        r.date = LocalDate.now(IST);
        r.stops = rows.stream().map(row -> {
            Station s = byCode.get(row.getStationCode());
            Instant arr = row.getSchArr() == null ? null : at(r.date, row.getDayOffset(), row.getSchArr());
            Instant dep = row.getSchDep() == null ? null : at(r.date, row.getDayOffset(), row.getSchDep());
            return new Stop(row.getSeq(), row.getStationCode(), s.getLatitude(), s.getLongitude(),
                    arr != null ? arr : dep, dep != null ? dep : arr, row.getDistanceKm());
        }).toList();
        r.idx = 0;
        r.clock = r.stops.get(0).dep().minus(Duration.ofMinutes(10));
        r.departAt = r.stops.get(0).dep().plusSeconds(initialDelaySeconds);
        this.run = r;
        return "Simulator started for " + trainNumber + " journeyDate=" + r.date + " (" + stepSeconds + "x speed)";
    }

    public synchronized void stop() {
        this.run = null;
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", run != null);
        m.put("trainNumber", trainNumber);
        if (run != null) {
            m.put("journeyDate", run.date.toString());
            m.put("simulatedTime", run.clock.toString());
            m.put("currentStation", run.stops.get(run.idx).code());
            m.put("phase", run.phase.name());
            m.put("pingsSent", run.pings);
        }
        return m;
    }

    @Scheduled(fixedDelayString = "${simulator.tick-ms:1000}", initialDelayString = "5000")
    public synchronized void tick() {
        if (run == null) {
            return;
        }
        try {
            advance(run);
        } catch (Exception ex) {
            log.warn("Simulator tick failed: {}", ex.toString());
        }
    }

    private void advance(Run r) {
        r.clock = r.clock.plusSeconds(stepSeconds);
        Stop cur = r.stops.get(r.idx);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        if (r.phase == Phase.WAITING) {
            if (!r.clock.isBefore(r.departAt)) {
                send(r, EventType.STATION_DEPARTURE, cur.code(), cur.lat(), cur.lon(), 0.0, "STATION_CROSSING");
                Stop next = r.stops.get(r.idx + 1);
                double scheduled = Duration.between(cur.dep(), next.arr()).toSeconds();
                r.segTotal = scheduled * (0.97 + rnd.nextDouble() * 0.10);
                r.elapsed = 0;
                r.phase = Phase.RUNNING;
            } else {
                send(r, EventType.POSITION, cur.code(), cur.lat(), cur.lon(), 0.0, "GPS");
            }
            return;
        }

        Stop next = r.stops.get(r.idx + 1);
        r.elapsed += stepSeconds;
        double frac = Math.min(1.0, r.elapsed / r.segTotal);
        if (frac >= 1.0) {
            send(r, EventType.STATION_ARRIVAL, next.code(), next.lat(), next.lon(), 15.0, "STATION_CROSSING");
            r.idx++;
            if (r.idx == r.stops.size() - 1) {
                log.info("Simulator: train {} reached terminus {}", trainNumber, next.code());
                run = null;
                return;
            }
            Duration halt = Duration.between(next.arr(), next.dep());
            if (halt.toSeconds() < 60) {
                halt = Duration.ofSeconds(60);
            }
            r.departAt = r.clock.plus(halt).plusSeconds(rnd.nextInt(0, 61));
            r.phase = Phase.WAITING;
            return;
        }
        double lat = cur.lat() + (next.lat() - cur.lat()) * frac;
        double lon = cur.lon() + (next.lon() - cur.lon()) * frac;
        double legKm = next.distKm() - cur.distKm();
        double avgKmph = legKm / (r.segTotal / 3600.0);
        double speed = Math.min(130.0, avgKmph * (0.92 + rnd.nextDouble() * 0.16));
        String source = (r.pings % 4 == 3) ? "CELL_TOWER" : "GPS";
        send(r, EventType.POSITION, null, lat, lon, Math.round(speed * 10) / 10.0, source);
    }

    private void send(Run r, EventType type, String stationCode, double lat, double lon, double speed, String source) {
        r.pings++;
        telemetry.ingest(new TelemetryRequest(trainNumber, r.date, type, stationCode, lat, lon, speed, source,
                r.clock, "SIM-" + trainNumber));
    }

    public synchronized TelemetryResponse injectAnomaly() {
        Run r = run;
        LocalDate date = r != null ? r.date : LocalDate.now(IST);
        Instant when = r != null ? r.clock.plusSeconds(1) : Instant.now();
        List<TrainSchedule> rows = schedules.findByTrainNumberOrderBySeqAsc(trainNumber);
        TrainSchedule last = rows.get(rows.size() - 1);
        Station s = stations.findById(last.getStationCode()).orElseThrow();
        return telemetry.ingest(new TelemetryRequest(trainNumber, date, EventType.STATION_ARRIVAL, s.getCode(),
                s.getLatitude(), s.getLongitude(), 90.0, "CELL_TOWER", when, "ANOMALY-INJECTOR"));
    }

    private static Instant at(LocalDate date, int dayOffset, java.time.LocalTime time) {
        return date.plusDays(dayOffset).atTime(time).atZone(IST).toInstant();
    }
}
