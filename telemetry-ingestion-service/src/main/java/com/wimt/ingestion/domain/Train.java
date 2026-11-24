package com.wimt.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trains")
@Getter @Setter @NoArgsConstructor
public class Train {
    @Id
    private String number;
    private String name;
    @Column(name = "train_type")
    private String trainType;
    @Column(name = "origin_code")
    private String originCode;
    @Column(name = "destination_code")
    private String destinationCode;
    @Column(name = "runs_on")
    private String runsOn;
    @Column(name = "max_speed_kmph")
    private int maxSpeedKmph;
}
