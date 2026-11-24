package com.wimt.eta.service;

import com.wimt.common.EtaCommand;
import com.wimt.common.EtaFailedEvent;
import com.wimt.common.EtaRecalculatedEvent;
import com.wimt.common.EtaRevertedEvent;
import com.wimt.common.Json;
import com.wimt.common.TelemetryReceivedEvent;
import com.wimt.common.Topics;
import com.wimt.eta.domain.EtaProjection;
import com.wimt.eta.domain.EtaProjectionRepository;
import com.wimt.eta.domain.OutboxEvent;
import com.wimt.eta.domain.OutboxEventRepository;
import com.wimt.eta.domain.ProcessedEvent;
import com.wimt.eta.domain.ProcessedEventRepository;
import com.wimt.eta.domain.StopActual;
import com.wimt.eta.domain.StopActualRepository;
import com.wimt.eta.domain.TrainState;
import com.wimt.eta.domain.TrainStateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CQRS command side of the ETA engine. Each saga command is handled in one ACID transaction that
 * updates domain state AND writes the resulting event to the outbox (published later by Debezium).
 * Commands are idempotent through the processed_events table.
 */
@Service
public class EtaCommandService {

    private final ScheduleRepository schedules;
    private final TrainStateRepository states;
    private final StopActualRepository actuals;
    private final EtaProjectionRepository projections;
    private final ProcessedEventRepository processed;
    private final OutboxEventRepository outbox;
    private final EtaCalculator calculator;
    private final MeterRegistry meters;

    public EtaCommandService(ScheduleRepository schedules, TrainStateRepository states, StopActualRepository actuals,
                             EtaProjectionRepository projections, ProcessedEventRepository processed,
                             OutboxEventRepository outbox, EtaCalculator calculator, MeterRegistry meters) {
        this.schedules = schedules;
        this.states = states;
        this.actuals = actuals;
        this.projections = projections;
        this.processed = processed;
        this.outbox = outbox;
        this.calculator = calculator;
        this.meters = meters;
    }

    @Transactional
    public void handle(EtaCommand cmd) {
        if (processed.existsById(cmd.commandId())) {
            return;
        }
        if (EtaCommand.REVERT.equals(cmd.type())) {
            revert(cmd);
        } else {
            calculate(cmd);
        }
        processed.save(new ProcessedEvent(cmd.commandId()));
    }

    // ------------------------------------------------------------------ calculate

    private void calculate(EtaCommand cmd) {
        TelemetryReceivedEvent t = cmd.telemetry();
        List<ScheduleStop> sched = schedules.findSchedule(t.trainNumber());
        if (sched.isEmpty()) {
            fail(cmd, t, "UNKNOWN_TRAIN_SCHEDULE: no schedule for " + t.trainNumber());
            return;
        }
        LocalDate date = t.journeyDate();
        TrainState st = states.findByTrainNumberAndJourneyDate(t.trainNumber(), date)
                .orElseGet(() -> new TrainState(t.trainNumber(), date));

        String anomaly = detectAnomaly(t, sched, st);
        if (anomaly != null) {
            fail(cmd, t, anomaly);
            return;
        }

        boolean stale = st.getLastEventAt() != null && t.recordedAt().isBefore(st.getLastEventAt());

        EtaProjection proj = new EtaProjection();
        proj.setSagaId(cmd.sagaId());
        proj.setTrainNumber(t.trainNumber());
        proj.setJourneyDate(date);
        proj.setPrevLastSeq(st.getLastSeq());
        proj.setPrevDeparted(st.isDeparted());
        proj.setPrevDelayMinutes(st.getDelayMinutes());
        proj.setPrevLatitude(st.getLatitude());
        proj.setPrevLongitude(st.getLongitude());
        proj.setPrevSpeedKmph(st.getSpeedKmph());
        proj.setPrevLastEventAt(st.getLastEventAt());

        StopActual touched = null;
        if (!stale) {
            st.setLatitude(t.latitude());
            st.setLongitude(t.longitude());
            st.setSpeedKmph(t.speedKmph());
            st.setLastEventAt(t.recordedAt());
            st.setUpdatedAt(Instant.now());
            switch (t.eventType()) {
                case "STATION_ARRIVAL" -> {
                    int seq = indexOf(sched, t.stationCode());
                    touched = touch(t, seq, proj);
                    touched.setActualArr(t.recordedAt());
                    st.setLastSeq(seq);
                    st.setDeparted(false);
                    ScheduleStop s = sched.get(seq);
                    Instant ref = seq == 0 ? s.departure(date) : s.arrival(date);
                    st.setDelayMinutes(clamp((int) EtaCalculator.minutes(ref, t.recordedAt())));
                }
                case "STATION_DEPARTURE" -> {
                    int seq = indexOf(sched, t.stationCode());
                    touched = touch(t, seq, proj);
                    touched.setActualDep(t.recordedAt());
                    if (st.getLastSeq() < seq) {
                        st.setLastSeq(seq);
                    }
                    st.setDeparted(true);
                    st.setDelayMinutes(clamp((int) EtaCalculator.minutes(sched.get(seq).departure(date), t.recordedAt())));
                }
                default -> st.setDelayMinutes(clamp(estimateDelayFromPosition(t, sched, st, date)));
            }
            states.save(st);
            if (touched != null) {
                actuals.save(touched);
            }
        } else if (st.getId() == null) {
            states.save(st);
        }

        Map<Integer, EtaCalculator.Actual> actualMap = new HashMap<>();
        for (StopActual sa : actuals.findByTrainNumberAndJourneyDate(t.trainNumber(), date)) {
            actualMap.put(sa.getSeq(), new EtaCalculator.Actual(sa.getActualArr(), sa.getActualDep()));
        }
        if (touched != null) {
            actualMap.put(touched.getSeq(), new EtaCalculator.Actual(touched.getActualArr(), touched.getActualDep()));
        }

        EtaCalculator.Projection p = calculator.project(date, sched,
                new EtaCalculator.Position(st.getLastSeq(), st.isDeparted(), st.getDelayMinutes()), actualMap);

        proj.setDelayMinutes(st.getDelayMinutes());
        proj = projections.save(proj);

        UUID eventId = UUID.randomUUID();
        EtaRecalculatedEvent event = new EtaRecalculatedEvent(eventId, cmd.sagaId(), proj.getId(), t.trainNumber(),
                schedules.findTrainName(t.trainNumber()).orElse(t.trainNumber()), date, t.eventType(), p.status(),
                p.lastCrossedStation(), p.nextStation(), st.getLatitude(), st.getLongitude(), st.getSpeedKmph(),
                p.delayMinutes(), t.recordedAt(), p.destinationEta(), p.stops());
        outbox.save(new OutboxEvent(eventId, "TrainEta", Topics.trainKey(t.trainNumber(), date),
                "EtaRecalculated", Json.write(event)));
        meters.counter("eta.recalculated").increment();
    }

    private StopActual touch(TelemetryReceivedEvent t, int seq, EtaProjection proj) {
        Optional<StopActual> existing = actuals.findByTrainNumberAndJourneyDateAndSeq(t.trainNumber(), t.journeyDate(), seq);
        proj.setTouchedSeq(seq);
        proj.setHadActualRow(existing.isPresent());
        existing.ifPresent(e -> {
            proj.setPrevActualArr(e.getActualArr());
            proj.setPrevActualDep(e.getActualDep());
        });
        return existing.orElseGet(() -> new StopActual(t.trainNumber(), t.journeyDate(), seq));
    }

    /** Domain invariants: returns an anomaly description or null when the telemetry is plausible. */
    String detectAnomaly(TelemetryReceivedEvent t, List<ScheduleStop> sched, TrainState st) {
        if (!"POSITION".equals(t.eventType())) {
            int seq = indexOf(sched, t.stationCode());
            if (seq < 0) {
                return "UNKNOWN_STATION_FOR_TRAIN: " + t.stationCode() + " is not on the route of " + t.trainNumber();
            }
            int last = st.getLastSeq();
            if (seq < last) {
                return "BACKWARD_STATION_MOVE: reported " + t.stationCode() + " (seq " + seq + ") after seq " + last;
            }
            if (seq > last + 2) {
                return "IMPOSSIBLE_STATION_JUMP: " + t.stationCode() + " (seq " + seq + ") reported while last known seq is " + last;
            }
        }
        if (st.getLastEventAt() != null && st.getLatitude() != null && t.recordedAt().isAfter(st.getLastEventAt())) {
            double hours = Duration.between(st.getLastEventAt(), t.recordedAt()).toSeconds() / 3600.0;
            double km = EtaCalculator.haversineKm(st.getLatitude(), st.getLongitude(), t.latitude(), t.longitude());
            if (hours > 0 && km > 0.5 && km / hours > 250.0) {
                return String.format("IMPOSSIBLE_SPEED: moved %.1f km in %.0f s (%.0f km/h)", km, hours * 3600, km / hours);
            }
        }
        return null;
    }

    private int estimateDelayFromPosition(TelemetryReceivedEvent t, List<ScheduleStop> sched, TrainState st, LocalDate date) {
        int n = sched.size();
        int eff = Math.max(st.getLastSeq(), 0);
        boolean departedEff = st.getLastSeq() >= 0 && st.isDeparted();
        int delay = st.getDelayMinutes();
        if (!departedEff) {
            if (eff < n - 1) {
                long late = EtaCalculator.minutes(sched.get(eff).departure(date), t.recordedAt());
                if (late > delay) {
                    delay = (int) late;
                }
            }
            return delay;
        }
        if (eff + 1 >= n) {
            return delay;
        }
        ScheduleStop cur = sched.get(eff);
        ScheduleStop next = sched.get(eff + 1);
        double remainingKm = EtaCalculator.haversineKm(t.latitude(), t.longitude(), next.lat(), next.lon());
        double legKm = next.distanceKm() - cur.distanceKm();
        double legHours = Duration.between(cur.departure(date), next.arrival(date)).toSeconds() / 3600.0;
        double scheduledAvg = legHours > 0 ? legKm / legHours : 80.0;
        double speed = Math.max(t.speedKmph() > 20 ? t.speedKmph() : scheduledAvg, 30.0);
        Instant eta = t.recordedAt().plusSeconds((long) (remainingKm / speed * 3600));
        int projected = (int) EtaCalculator.minutes(next.arrival(date), eta);
        return (int) Math.round(0.7 * delay + 0.3 * projected);
    }

    private void fail(EtaCommand cmd, TelemetryReceivedEvent t, String reason) {
        UUID eventId = UUID.randomUUID();
        EtaFailedEvent event = new EtaFailedEvent(eventId, cmd.sagaId(), t.pingId(), t.trainNumber(), t.journeyDate(), reason);
        outbox.save(new OutboxEvent(eventId, "TrainEta", Topics.trainKey(t.trainNumber(), t.journeyDate()),
                "EtaFailed", Json.write(event)));
        meters.counter("eta.failed").increment();
    }

    // ------------------------------------------------------------------ compensation

    private void revert(EtaCommand cmd) {
        Optional<EtaProjection> found = projections.findById(cmd.projectionId());
        String train = null;
        LocalDate date = null;
        if (found.isPresent() && !found.get().isReverted()) {
            EtaProjection p = found.get();
            train = p.getTrainNumber();
            date = p.getJourneyDate();
            boolean latest = projections.findFirstByTrainNumberAndJourneyDateOrderByIdDesc(train, date)
                    .map(l -> l.getId().equals(p.getId())).orElse(false);
            if (latest) {
                states.findByTrainNumberAndJourneyDate(train, date).ifPresent(st -> {
                    st.setLastSeq(p.getPrevLastSeq());
                    st.setDeparted(p.isPrevDeparted());
                    st.setDelayMinutes(p.getPrevDelayMinutes());
                    st.setLatitude(p.getPrevLatitude());
                    st.setLongitude(p.getPrevLongitude());
                    st.setSpeedKmph(p.getPrevSpeedKmph());
                    st.setLastEventAt(p.getPrevLastEventAt());
                    st.setUpdatedAt(Instant.now());
                    states.save(st);
                });
                if (p.getTouchedSeq() != null) {
                    actuals.findByTrainNumberAndJourneyDateAndSeq(train, date, p.getTouchedSeq()).ifPresent(sa -> {
                        if (p.isHadActualRow()) {
                            sa.setActualArr(p.getPrevActualArr());
                            sa.setActualDep(p.getPrevActualDep());
                            actuals.save(sa);
                        } else {
                            actuals.delete(sa);
                        }
                    });
                }
            }
            p.setReverted(true);
            projections.save(p);
            meters.counter("eta.reverted").increment();
        } else if (found.isPresent()) {
            train = found.get().getTrainNumber();
            date = found.get().getJourneyDate();
        }
        UUID eventId = UUID.randomUUID();
        String key = train == null ? "unknown" : Topics.trainKey(train, date);
        outbox.save(new OutboxEvent(eventId, "TrainEta", key, "EtaReverted",
                Json.write(new EtaRevertedEvent(eventId, cmd.sagaId(), train, date))));
    }

    private static int indexOf(List<ScheduleStop> sched, String code) {
        for (ScheduleStop s : sched) {
            if (s.code().equals(code)) {
                return s.seq();
            }
        }
        return -1;
    }

    private static int clamp(int minutes) {
        return Math.max(-30, Math.min(720, minutes));
    }
}
