package com.wimt.common;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Saga command to train-query-service: apply ETA projection to boards/live views and push over WebSocket. */
public record BoardCommand(UUID commandId, UUID sagaId, EtaRecalculatedEvent eta) {
    public static BoardCommand of(UUID sagaId, EtaRecalculatedEvent eta) {
        return new BoardCommand(UUID.nameUUIDFromBytes((sagaId + ":board").getBytes(StandardCharsets.UTF_8)), sagaId, eta);
    }
}
