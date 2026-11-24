package com.wimt.eta.service;

import com.wimt.common.EtaCommand;
import com.wimt.common.Json;
import com.wimt.common.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes saga commands (CALCULATE / REVERT) from the orchestrator. */
@Component
public class EtaCommandListener {
    private final EtaCommandService service;

    public EtaCommandListener(EtaCommandService service) {
        this.service = service;
    }

    @KafkaListener(topics = Topics.SAGA_ETA_COMMANDS, groupId = "eta-engine")
    public void onCommand(String message) {
        service.handle(Json.read(message, EtaCommand.class));
    }
}
