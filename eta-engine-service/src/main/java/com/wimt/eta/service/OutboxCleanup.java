package com.wimt.eta.service;

import com.wimt.eta.domain.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxCleanup {
    private final OutboxEventRepository outbox;

    public OutboxCleanup(OutboxEventRepository outbox) {
        this.outbox = outbox;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    @Transactional
    public void purge() {
        outbox.purgeOlderThan(Instant.now().minus(Duration.ofHours(1)));
    }
}
