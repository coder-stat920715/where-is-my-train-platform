package com.wimt.query.service;

import com.wimt.common.BoardCommand;
import com.wimt.common.Json;
import com.wimt.common.SagaReply;
import com.wimt.common.Topics;
import com.wimt.query.store.ViewStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Saga participant on the query side. Consumes board-update commands idempotently (Redis dedup set),
 * applies the projection and replies success / failure to the orchestrator.
 */
@Component
public class BoardCommandListener {
    private static final Logger log = LoggerFactory.getLogger(BoardCommandListener.class);

    private final ProjectionService projection;
    private final ViewStore store;
    private final KafkaTemplate<String, String> kafka;

    public BoardCommandListener(ProjectionService projection, ViewStore store, KafkaTemplate<String, String> kafka) {
        this.projection = projection;
        this.store = store;
        this.kafka = kafka;
    }

    @KafkaListener(topics = Topics.SAGA_BOARD_COMMANDS, groupId = "train-query")
    public void onCommand(String message) {
        BoardCommand cmd = Json.read(message, BoardCommand.class);
        String key = Topics.trainKey(cmd.eta().trainNumber(), cmd.eta().journeyDate());
        try {
            if (!store.markIfNew(cmd.commandId())) {
                reply(key, new SagaReply(cmd.sagaId(), SagaReply.STEP_BOARD, true, "duplicate command ignored"));
                return;
            }
        } catch (RuntimeException e) {
            reply(key, new SagaReply(cmd.sagaId(), SagaReply.STEP_BOARD, false, "dedup store unavailable: " + e));
            return;
        }
        try {
            projection.apply(cmd.eta());
            reply(key, new SagaReply(cmd.sagaId(), SagaReply.STEP_BOARD, true, null));
        } catch (RuntimeException e) {
            log.error("Board update failed for saga {}: {}", cmd.sagaId(), e.toString());
            try {
                store.unmark(cmd.commandId());
            } catch (RuntimeException ignored) {
                // best effort; the saga will compensate
            }
            reply(key, new SagaReply(cmd.sagaId(), SagaReply.STEP_BOARD, false, e.toString()));
        }
    }

    private void reply(String key, SagaReply reply) {
        kafka.send(Topics.SAGA_REPLIES, key, Json.write(reply));
    }
}
