package com.wimt.ingestion.service;

import com.wimt.common.AnomalyCommand;
import com.wimt.common.Json;
import com.wimt.common.Topics;
import com.wimt.ingestion.domain.Repositories.TelemetryPingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Saga compensation: flags a corrupted / impossible ping so analytics can exclude it. Idempotent. */
@Component
public class AnomalyCommandListener {
    private static final Logger log = LoggerFactory.getLogger(AnomalyCommandListener.class);

    private final TelemetryPingRepository pings;

    public AnomalyCommandListener(TelemetryPingRepository pings) {
        this.pings = pings;
    }

    @KafkaListener(topics = Topics.SAGA_TELEMETRY_COMMANDS, groupId = "telemetry-ingestion")
    @Transactional
    public void onCommand(String message) {
        AnomalyCommand cmd = Json.read(message, AnomalyCommand.class);
        int updated = pings.markAnomaly(cmd.pingId(), cmd.reason());
        log.warn("Saga {} flagged telemetry ping {} as anomalous ({}), rows={}", cmd.sagaId(), cmd.pingId(), cmd.reason(), updated);
    }
}
