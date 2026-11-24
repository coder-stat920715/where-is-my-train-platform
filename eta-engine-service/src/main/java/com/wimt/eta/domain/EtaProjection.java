package com.wimt.eta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An ETA recalculation plus the snapshot needed to compensate (revert) it. */
@Entity
@Table(name = "eta_projections")
@Getter @Setter @NoArgsConstructor
public class EtaProjection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;
    @Column(name = "train_number", nullable = false)
    private String trainNumber;
    @Column(name = "journey_date", nullable = false)
    private LocalDate journeyDate;
    @Column(name = "delay_minutes", nullable = false)
    private int delayMinutes;
    private boolean reverted;
    @Column(name = "prev_last_seq", nullable = false)
    private int prevLastSeq;
    @Column(name = "prev_departed", nullable = false)
    private boolean prevDeparted;
    @Column(name = "prev_delay_minutes", nullable = false)
    private int prevDelayMinutes;
    @Column(name = "prev_latitude")
    private Double prevLatitude;
    @Column(name = "prev_longitude")
    private Double prevLongitude;
    @Column(name = "prev_speed_kmph")
    private Double prevSpeedKmph;
    @Column(name = "prev_last_event_at")
    private Instant prevLastEventAt;
    @Column(name = "touched_seq")
    private Integer touchedSeq;
    @Column(name = "had_actual_row", nullable = false)
    private boolean hadActualRow;
    @Column(name = "prev_actual_arr")
    private Instant prevActualArr;
    @Column(name = "prev_actual_dep")
    private Instant prevActualDep;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
