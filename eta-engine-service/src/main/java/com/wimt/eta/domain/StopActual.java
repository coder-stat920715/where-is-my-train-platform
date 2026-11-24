package com.wimt.eta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stop_actuals")
@Getter @Setter @NoArgsConstructor
public class StopActual {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "train_number", nullable = false)
    private String trainNumber;
    @Column(name = "journey_date", nullable = false)
    private LocalDate journeyDate;
    @Column(nullable = false)
    private int seq;
    @Column(name = "actual_arr")
    private Instant actualArr;
    @Column(name = "actual_dep")
    private Instant actualDep;

    public StopActual(String trainNumber, LocalDate journeyDate, int seq) {
        this.trainNumber = trainNumber;
        this.journeyDate = journeyDate;
        this.seq = seq;
    }
}
