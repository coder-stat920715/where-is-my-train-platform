package com.wimt.saga.service;

import com.wimt.common.EtaFailedEvent;
import com.wimt.common.EtaRecalculatedEvent;
import com.wimt.common.EtaRevertedEvent;
import com.wimt.common.Json;
import com.wimt.common.SagaReply;
import com.wimt.common.TelemetryReceivedEvent;
import com.wimt.common.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka entry points of the orchestrator. Debezium-routed outbox topics carry the raw JSON payload. */
@Component
public class SagaKafkaListeners {
    private static final String GROUP = "saga-orchestrator";
    private final SagaOrchestrator orchestrator;

    public SagaKafkaListeners(SagaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @KafkaListener(topics = Topics.OUTBOX_TELEMETRY_RECEIVED, groupId = GROUP)
    public void telemetryReceived(String message) {
        orchestrator.onTelemetryReceived(Json.read(message, TelemetryReceivedEvent.class));
    }

    @KafkaListener(topics = Topics.OUTBOX_ETA_RECALCULATED, groupId = GROUP)
    public void etaRecalculated(String message) {
        orchestrator.onEtaRecalculated(Json.read(message, EtaRecalculatedEvent.class));
    }

    @KafkaListener(topics = Topics.OUTBOX_ETA_FAILED, groupId = GROUP)
    public void etaFailed(String message) {
        orchestrator.onEtaFailed(Json.read(message, EtaFailedEvent.class));
    }

    @KafkaListener(topics = Topics.OUTBOX_ETA_REVERTED, groupId = GROUP)
    public void etaReverted(String message) {
        orchestrator.onEtaReverted(Json.read(message, EtaRevertedEvent.class));
    }

    @KafkaListener(topics = Topics.SAGA_REPLIES, groupId = GROUP)
    public void boardReply(String message) {
        orchestrator.onBoardReply(Json.read(message, SagaReply.class));
    }
}
