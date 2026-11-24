package com.wimt.common;

import java.time.LocalDate;
import java.util.UUID;

/** Outbox event type "EtaReverted": compensation of an ETA projection completed. */
public record EtaRevertedEvent(
        UUID eventId,
        UUID sagaId,
        String trainNumber,
        LocalDate journeyDate) {
}
