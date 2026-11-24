package com.wimt.eta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Write-side aggregate: the authoritative running state of one train journey. */
@Entity
@Table(name = "train_state")
@Getter @Setter @NoArgsConstructor
public class TrainState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "train_number", nullable = false)
    private String trainNumber;
    @Column(name = "journey_date", nullable = false)
    private LocalDate journeyDate;
    @Column(name = "last_seq", nullable = false)
    private int lastSeq = -1;
    @Column(nullable = false)
    private boolean departed;
    @Column(name = "delay_minutes", nullable = false)
    private int delayMinutes;
    private Double latitude;
    private Double longitude;
    @Column(name = "speed_kmph")
    private Double speedKmph;
    @Column(name = "last_event_at")
    private Instant lastEventAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    public TrainState(String trainNumber, LocalDate journeyDate) {
        this.trainNumber = trainNumber;
        this.journeyDate = journeyDate;
    }
}
