package com.wimt.eta.service;

import com.wimt.common.StopProjection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure ETA propagation logic. Given the current running position and delay of a train it projects
 * expected arrival/departure for every remaining stop. A delay is partially recovered on each
 * section (6 % running-time padding, max 8 min) and halts can be shortened to a 2-minute minimum.
 */
@Component
public class EtaCalculator {

    public static final int MIN_HALT_MINUTES = 2;

    public record Position(int lastSeq, boolean departed, int delayMinutes) {}

    public record Actual(Instant arr, Instant dep) {}

    public record Projection(List<StopProjection> stops, String status, String lastCrossedStation,
                             String nextStation, Instant destinationEta, int delayMinutes) {}

    public Projection project(LocalDate date, List<ScheduleStop> sched, Position pos, Map<Integer, Actual> actuals) {
        int n = sched.size();
        int eff = Math.max(pos.lastSeq(), 0);
        boolean departedEff = pos.lastSeq() >= 0 && pos.departed();
        Instant[] expArr = new Instant[n];
        Instant[] expDep = new Instant[n];
        int carry = 0;

        for (int i = 0; i < n; i++) {
            ScheduleStop s = sched.get(i);
            Instant schArr = s.arrival(date);
            Instant schDep = s.departure(date);
            Actual a = actuals.get(i);

            if (i < eff || (i == eff && departedEff)) {
                expArr[i] = a != null && a.arr() != null ? a.arr() : schArr;
                expDep[i] = a != null && a.dep() != null ? a.dep() : schDep;
                if (i == eff) {
                    carry = pos.delayMinutes();
                }
            } else if (i == eff) {
                Instant arr;
                Instant dep;
                if (a != null && a.arr() != null) {
                    arr = a.arr();
                } else if (pos.lastSeq() < 0) {
                    arr = schArr;
                } else {
                    arr = schArr.plusSeconds(60L * Math.max(pos.delayMinutes(), 0));
                }
                if (i == n - 1) {
                    dep = arr;
                } else if (pos.lastSeq() < 0 && i == 0) {
                    dep = schDep.plusSeconds(60L * Math.max(pos.delayMinutes(), 0));
                } else {
                    dep = max(schDep, arr.plusSeconds(60L * MIN_HALT_MINUTES));
                }
                expArr[i] = arr;
                expDep[i] = dep;
                carry = (int) minutes(schDep, dep);
            } else {
                ScheduleStop prev = sched.get(i - 1);
                long legMinutes = minutes(prev.departure(date), schArr);
                int recovery = (int) Math.min(8, Math.round(legMinutes * 0.06));
                int arrDelay = Math.max(0, carry - recovery);
                expArr[i] = schArr.plusSeconds(60L * arrDelay);
                boolean terminal = i == n - 1;
                long scheduledHalt = minutes(schArr, schDep);
                expDep[i] = terminal ? expArr[i]
                        : max(schDep, expArr[i].plusSeconds(scheduledHalt > 0 ? 60L * MIN_HALT_MINUTES : 0));
                carry = (int) minutes(schDep, expDep[i]);
            }
        }

        List<StopProjection> stops = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ScheduleStop s = sched.get(i);
            Actual a = actuals.get(i);
            boolean crossed = i < eff || (i == eff && departedEff);
            stops.add(new StopProjection(s.seq(), s.code(), s.name(), s.platform(),
                    s.arrival(date), s.departure(date), expArr[i], expDep[i],
                    a == null ? null : a.arr(), a == null ? null : a.dep(),
                    (int) minutes(s.arrival(date), expArr[i]), (int) minutes(s.departure(date), expDep[i]), crossed));
        }

        String status;
        if (pos.lastSeq() < 0) {
            status = "NOT_STARTED";
        } else if (pos.lastSeq() == n - 1) {
            status = "COMPLETED";
        } else {
            status = "RUNNING";
        }
        String lastCrossed = pos.lastSeq() >= 0 ? sched.get(pos.lastSeq()).code() : null;
        int nextIdx = pos.lastSeq() < 0 ? 0 : pos.lastSeq() + 1;
        String next = nextIdx < n ? sched.get(nextIdx).code() : null;
        return new Projection(stops, status, lastCrossed, next, expArr[n - 1], pos.delayMinutes());
    }

    public static long minutes(Instant from, Instant to) {
        return Math.round(Duration.between(from, to).toSeconds() / 60.0);
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    /** Great-circle distance in kilometres. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
