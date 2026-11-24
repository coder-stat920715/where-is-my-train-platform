package com.wimt.eta.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** A scheduled stop of a train joined with station reference data. */
public record ScheduleStop(int seq, String code, String name, double lat, double lon, String platform,
                           LocalTime schArr, LocalTime schDep, int dayOffset, double distanceKm) {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public Instant arrival(LocalDate journeyDate) {
        return at(journeyDate, schArr != null ? schArr : schDep);
    }

    public Instant departure(LocalDate journeyDate) {
        return at(journeyDate, schDep != null ? schDep : schArr);
    }

    private Instant at(LocalDate journeyDate, LocalTime time) {
        return journeyDate.plusDays(dayOffset).atTime(time).atZone(IST).toInstant();
    }
}
