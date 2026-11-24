package com.wimt.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "train_schedule")
@Getter @Setter @NoArgsConstructor
public class TrainSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "train_number")
    private String trainNumber;
    private int seq;
    @Column(name = "station_code")
    private String stationCode;
    @Column(name = "sch_arr")
    private LocalTime schArr;
    @Column(name = "sch_dep")
    private LocalTime schDep;
    @Column(name = "day_offset")
    private int dayOffset;
    private String platform;
    @Column(name = "distance_km")
    private double distanceKm;
}
