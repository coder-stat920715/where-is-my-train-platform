package com.wimt.ingestion.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stations")
@Getter @Setter @NoArgsConstructor
public class Station {
    @Id
    private String code;
    private String name;
    private double latitude;
    private double longitude;
}
