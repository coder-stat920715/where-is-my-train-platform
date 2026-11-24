package com.wimt.ingestion.domain;

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
@Table(name = "telemetry_pings")
@Getter @Setter @NoArgsConstructor
public class TelemetryPing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "train_number", nullable = false)
    private String trainNumber;
    @Column(name = "journey_date", nullable = false)
    private LocalDate journeyDate;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "station_code")
    private String stationCode;
    @Column(nullable = false)
    private double latitude;
    @Column(nullable = false)
    private double longitude;
    @Column(name = "speed_kmph", nullable = false)
    private double speedKmph;
    @Column(nullable = false)
    private String source;
    @Column(name = "device_id")
    private String deviceId;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    @Column(name = "dedupe_key", nullable = false, unique = true)
    private String dedupeKey;
    private boolean anomaly;
    @Column(name = "anomaly_reason")
    private String anomalyReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
