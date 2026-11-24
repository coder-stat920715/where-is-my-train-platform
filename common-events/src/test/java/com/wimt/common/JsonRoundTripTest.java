package com.wimt.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonRoundTripTest {

    @Test
    void telemetryEventSurvivesJsonRoundTrip() {
        var event = new TelemetryReceivedEvent(UUID.randomUUID(), 42L, "22895", LocalDate.of(2026, 11, 17),
                "STATION_ARRIVAL", "KGP", 22.3396, 87.3202, 15.0, "STATION_CROSSING", Instant.parse("2026-11-17T02:00:00Z"));
        assertEquals(event, Json.read(Json.write(event), TelemetryReceivedEvent.class));
    }

    @Test
    void commandIdsAreDeterministicSoRedeliveryIsIdempotent() {
        UUID saga = UUID.randomUUID();
        assertEquals(EtaCommand.revert(saga, 1L).commandId(), EtaCommand.revert(saga, 2L).commandId());
        assertEquals(AnomalyCommand.of(saga, 1, "x").commandId(), AnomalyCommand.of(saga, 1, "y").commandId());
    }
}
