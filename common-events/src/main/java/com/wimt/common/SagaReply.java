package com.wimt.common;

import java.util.UUID;

/** Reply from a saga participant that has no outbox (query side). */
public record SagaReply(UUID sagaId, String step, boolean success, String reason) {
    public static final String STEP_BOARD = "BOARD_UPDATE";
}
