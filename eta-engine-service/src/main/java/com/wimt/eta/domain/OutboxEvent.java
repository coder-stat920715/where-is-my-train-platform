package com.wimt.eta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Transactional outbox row captured by Debezium (EventRouter SMT). */
@Entity
@Table(name = "outbox_events")
@Getter @Setter @NoArgsConstructor
public class OutboxEvent {
    @Id
    private UUID id;
    @Column(name = "aggregatetype", nullable = false)
    private String aggregateType;
    @Column(name = "aggregateid", nullable = false)
    private String aggregateId;
    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private String payload;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public OutboxEvent(UUID id, String aggregateType, String aggregateId, String type, String payload) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
    }
}
