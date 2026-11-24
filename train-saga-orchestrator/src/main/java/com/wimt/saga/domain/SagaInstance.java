package com.wimt.saga.domain;

import com.wimt.common.SagaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persistent saga state machine row. */
@Entity
@Table(name = "saga_instances")
@Getter @Setter @NoArgsConstructor
public class SagaInstance {
    @Id
    private UUID id;
    @Column(name = "trigger_event_id", nullable = false, unique = true)
    private UUID triggerEventId;
    @Column(name = "train_number", nullable = false)
    private String trainNumber;
    @Column(name = "journey_date", nullable = false)
    private LocalDate journeyDate;
    @Column(name = "ping_id", nullable = false)
    private long pingId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus state;
    @Column(name = "projection_id")
    private Long projectionId;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(columnDefinition = "text")
    private String history;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    public static SagaInstance start(UUID triggerEventId, String trainNumber, LocalDate journeyDate, long pingId) {
        SagaInstance s = new SagaInstance();
        s.id = UUID.randomUUID();
        s.triggerEventId = triggerEventId;
        s.trainNumber = trainNumber;
        s.journeyDate = journeyDate;
        s.pingId = pingId;
        s.transition(SagaStatus.STARTED, "saga started by TelemetryReceived " + triggerEventId);
        return s;
    }

    public void transition(SagaStatus next, String detail) {
        this.state = next;
        this.updatedAt = Instant.now();
        String line = updatedAt + " | " + next + " | " + detail;
        this.history = (history == null || history.isEmpty()) ? line : history + "\n" + line;
    }
}
