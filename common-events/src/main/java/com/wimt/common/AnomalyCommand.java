package com.wimt.common;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Saga compensation command to telemetry-ingestion-service: flag a ping as anomalous. */
public record AnomalyCommand(UUID commandId, UUID sagaId, long pingId, String reason) {
    public static AnomalyCommand of(UUID sagaId, long pingId, String reason) {
        return new AnomalyCommand(UUID.nameUUIDFromBytes((sagaId + ":anomaly").getBytes(StandardCharsets.UTF_8)),
                sagaId, pingId, reason);
    }
}
