package com.wimt.saga.service;

import com.wimt.common.AnomalyCommand;
import com.wimt.common.BoardCommand;
import com.wimt.common.EtaCommand;
import com.wimt.common.EtaFailedEvent;
import com.wimt.common.EtaRecalculatedEvent;
import com.wimt.common.EtaRevertedEvent;
import com.wimt.common.Json;
import com.wimt.common.SagaReply;
import com.wimt.common.SagaStatus;
import com.wimt.common.TelemetryReceivedEvent;
import com.wimt.common.Topics;
import com.wimt.saga.domain.SagaInstance;
import com.wimt.saga.domain.SagaInstanceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Orchestration-based saga. State is persisted in saga_instances BEFORE each command is sent (the
 * send happens after the DB commit). Lost sends / lost replies are healed by the timeout sweeper.
 *
 * Happy path : STARTED -> ETA_CALCULATED -> BOARD_UPDATED -> COMPLETED
 * Anomaly    : STARTED -> COMPENSATING (flag telemetry anomaly) -> FAILED
 * Board fail : ETA_CALCULATED -> COMPENSATING (revert ETA projection) -> FAILED
 */
@Service
public class SagaOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SagaInstanceRepository sagas;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry meters;
    private final Duration stepTimeout;

    public SagaOrchestrator(SagaInstanceRepository sagas, KafkaTemplate<String, String> kafka, MeterRegistry meters,
                            @Value("${saga.step-timeout-seconds:30}") long timeoutSeconds) {
        this.sagas = sagas;
        this.kafka = kafka;
        this.meters = meters;
        this.stepTimeout = Duration.ofSeconds(timeoutSeconds);
    }

    // ---------------------------------------------------------------- triggers

    /** TelemetryReceived (from Debezium) -> start saga, ask the ETA engine to recalculate. */
    @Transactional
    public void onTelemetryReceived(TelemetryReceivedEvent event) {
        if (sagas.existsByTriggerEventId(event.eventId())) {
            return;
        }
        SagaInstance saga = sagas.save(SagaInstance.start(event.eventId(), event.trainNumber(), event.journeyDate(), event.pingId()));
        meters.counter("saga.started").increment();
        send(Topics.SAGA_ETA_COMMANDS, key(saga), EtaCommand.calculate(saga.getId(), event));
    }

    /** EtaRecalculated -> ETA_CALCULATED, ask the query side to update boards. */
    @Transactional
    public void onEtaRecalculated(EtaRecalculatedEvent event) {
        Optional<SagaInstance> found = sagas.findById(event.sagaId());
        if (found.isEmpty() || found.get().getState() != SagaStatus.STARTED) {
            return;
        }
        SagaInstance saga = found.get();
        saga.setProjectionId(event.projectionId());
        saga.transition(SagaStatus.ETA_CALCULATED, "ETA projection " + event.projectionId() + " delay=" + event.delayMinutes() + "m");
        sagas.save(saga);
        send(Topics.SAGA_BOARD_COMMANDS, key(saga), BoardCommand.of(saga.getId(), event));
    }

    /** EtaFailed (anomaly) -> compensate: flag the telemetry ping, saga FAILED. */
    @Transactional
    public void onEtaFailed(EtaFailedEvent event) {
        Optional<SagaInstance> found = sagas.findById(event.sagaId());
        if (found.isEmpty() || found.get().getState() != SagaStatus.STARTED) {
            return;
        }
        SagaInstance saga = found.get();
        saga.setFailureReason(truncate(event.reason()));
        saga.transition(SagaStatus.COMPENSATING, "ETA rejected telemetry: " + event.reason());
        send(Topics.SAGA_TELEMETRY_COMMANDS, key(saga), AnomalyCommand.of(saga.getId(), saga.getPingId(), truncate(event.reason())));
        // ETA transaction rolled back nothing to revert; flag command is fire-and-forget and idempotent
        saga.transition(SagaStatus.FAILED, "telemetry ping " + saga.getPingId() + " flagged as anomaly; ETA projection discarded");
        sagas.save(saga);
        meters.counter("saga.failed", "reason", "anomaly").increment();
        log.warn("Saga {} FAILED (anomaly): {}", saga.getId(), event.reason());
    }

    /** Reply from the query side after it tried to update boards. */
    @Transactional
    public void onBoardReply(SagaReply reply) {
        Optional<SagaInstance> found = sagas.findById(reply.sagaId());
        if (found.isEmpty() || found.get().getState() != SagaStatus.ETA_CALCULATED) {
            return;
        }
        SagaInstance saga = found.get();
        if (reply.success()) {
            saga.transition(SagaStatus.BOARD_UPDATED, "station boards + live view updated");
            saga.transition(SagaStatus.COMPLETED, "saga completed");
            sagas.save(saga);
            meters.counter("saga.completed").increment();
        } else {
            saga.setFailureReason(truncate("BOARD_UPDATE_FAILED: " + reply.reason()));
            saga.transition(SagaStatus.COMPENSATING, "board update failed (" + reply.reason() + "); reverting ETA projection");
            sagas.save(saga);
            send(Topics.SAGA_ETA_COMMANDS, key(saga), EtaCommand.revert(saga.getId(), saga.getProjectionId()));
        }
    }

    /** EtaReverted -> compensation finished. */
    @Transactional
    public void onEtaReverted(EtaRevertedEvent event) {
        Optional<SagaInstance> found = sagas.findById(event.sagaId());
        if (found.isEmpty() || found.get().getState() != SagaStatus.COMPENSATING) {
            return;
        }
        SagaInstance saga = found.get();
        saga.transition(SagaStatus.FAILED, "ETA projection reverted");
        sagas.save(saga);
        meters.counter("saga.failed", "reason", "board").increment();
    }

    // ---------------------------------------------------------------- timeouts

    @Scheduled(fixedDelayString = "${saga.sweep-interval-ms:10000}", initialDelayString = "15000")
    @Transactional
    public void sweepTimedOut() {
        Instant cutoff = Instant.now().minus(stepTimeout);
        List<SagaInstance> stuck = sagas.findByStateInAndUpdatedAtBefore(
                EnumSet.of(SagaStatus.STARTED, SagaStatus.ETA_CALCULATED, SagaStatus.COMPENSATING), cutoff);
        for (SagaInstance saga : stuck) {
            switch (saga.getState()) {
                case STARTED -> {
                    saga.setFailureReason("TIMEOUT_WAITING_FOR_ETA");
                    saga.transition(SagaStatus.FAILED, "no reply from eta-engine within " + stepTimeout.toSeconds() + "s");
                }
                case ETA_CALCULATED -> {
                    saga.setFailureReason("TIMEOUT_WAITING_FOR_BOARD_UPDATE");
                    saga.transition(SagaStatus.COMPENSATING, "no board reply; reverting ETA projection");
                    send(Topics.SAGA_ETA_COMMANDS, key(saga), EtaCommand.revert(saga.getId(), saga.getProjectionId()));
                }
                case COMPENSATING -> saga.transition(SagaStatus.FAILED, "compensation timed out");
                default -> { }
            }
            sagas.save(saga);
            meters.counter("saga.timeouts").increment();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String key(SagaInstance saga) {
        return Topics.trainKey(saga.getTrainNumber(), saga.getJourneyDate());
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 480 ? s.substring(0, 480) : s);
    }

    /** Publishes only after the saga state transaction has committed. */
    private void send(String topic, String key, Object payload) {
        String json = Json.write(payload);
        Runnable publish = () -> kafka.send(topic, key, json).whenComplete((r, ex) -> {
            if (ex != null) {
                log.error("Failed to send saga command to {}: {}", topic, ex.toString());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
