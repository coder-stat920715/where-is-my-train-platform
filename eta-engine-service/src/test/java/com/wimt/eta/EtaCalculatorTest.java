package com.wimt.eta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wimt.common.StopProjection;
import com.wimt.eta.service.EtaCalculator;
import com.wimt.eta.service.EtaCalculator.Position;
import com.wimt.eta.service.ScheduleStop;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EtaCalculatorTest {

    private final EtaCalculator calculator = new EtaCalculator();
    private final LocalDate date = LocalDate.of(2026, 11, 17);

    private List<ScheduleStop> schedule() {
        return List.of(
                new ScheduleStop(0, "HWH", "Howrah", 22.58, 88.34, "8", null, LocalTime.of(6, 10), 0, 0),
                new ScheduleStop(1, "KGP", "Kharagpur", 22.34, 87.32, "3", LocalTime.of(7, 25), LocalTime.of(7, 27), 0, 116),
                new ScheduleStop(2, "BLS", "Balasore", 21.49, 86.91, "1", LocalTime.of(8, 50), LocalTime.of(8, 52), 0, 233),
                new ScheduleStop(3, "PURI", "Puri", 19.79, 85.82, "1", LocalTime.of(12, 15), null, 0, 500));
    }

    @Test
    void notStartedTrainHasNoDelayAndNothingCrossed() {
        var p = calculator.project(date, schedule(), new Position(-1, false, 0), Map.of());
        assertEquals("NOT_STARTED", p.status());
        assertEquals("HWH", p.nextStation());
        assertTrue(p.stops().stream().noneMatch(StopProjection::crossed));
        assertEquals(0, p.stops().get(3).arrivalDelayMinutes());
    }

    @Test
    void delayIsPartiallyRecoveredDownstreamAndStopsAreMarkedCrossed() {
        // departed Howrah 10 minutes late
        var p = calculator.project(date, schedule(), new Position(0, true, 10), Map.of());
        assertEquals("RUNNING", p.status());
        assertEquals("HWH", p.lastCrossedStation());
        assertEquals("KGP", p.nextStation());
        assertTrue(p.stops().get(0).crossed());
        assertFalse(p.stops().get(1).crossed());
        int kgpDelay = p.stops().get(1).arrivalDelayMinutes();
        assertTrue(kgpDelay < 10 && kgpDelay > 0, "delay should be partly recovered, was " + kgpDelay);
        assertTrue(p.delayMinutes() == 10);
        assertTrue(p.stops().get(3).arrivalDelayMinutes() <= kgpDelay);
    }

    @Test
    void arrivedAtTerminusIsCompleted() {
        var p = calculator.project(date, schedule(), new Position(3, false, 3), Map.of());
        assertEquals("COMPLETED", p.status());
        assertEquals("PURI", p.lastCrossedStation());
        assertEquals(null, p.nextStation());
    }

    @Test
    void haversineHowrahToKharagpurIsAboutOneHundredAndEightKm() {
        double km = EtaCalculator.haversineKm(22.5839, 88.3425, 22.3396, 87.3202);
        assertTrue(km > 100 && km < 120, "was " + km);
    }
}
