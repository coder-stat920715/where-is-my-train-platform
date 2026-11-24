package com.wimt.common;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Saga command to eta-engine-service. type = CALCULATE | REVERT. */
public record EtaCommand(
        UUID commandId,
        UUID sagaId,
        String type,
        TelemetryReceivedEvent telemetry,
        Long projectionId) {

    public static final String CALCULATE = "CALCULATE";
    public static final String REVERT = "REVERT";

    public static EtaCommand calculate(UUID sagaId, TelemetryReceivedEvent telemetry) {
        return new EtaCommand(UUID.nameUUIDFromBytes((sagaId + ":calc").getBytes(StandardCharsets.UTF_8)),
                sagaId, CALCULATE, telemetry, null);
    }

    public static EtaCommand revert(UUID sagaId, long projectionId) {
        return new EtaCommand(UUID.nameUUIDFromBytes((sagaId + ":revert").getBytes(StandardCharsets.UTF_8)),
                sagaId, REVERT, null, projectionId);
    }
}
