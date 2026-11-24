package com.wimt.ingestion.service;

import com.wimt.common.Json;
import com.wimt.common.TelemetryReceivedEvent;
import com.wimt.common.Topics;
import com.wimt.ingestion.api.TelemetryRequest;
import com.wimt.ingestion.api.TelemetryResponse;
import com.wimt.ingestion.domain.OutboxEvent;
import com.wimt.ingestion.domain.Repositories.OutboxEventRepository;
import com.wimt.ingestion.domain.Repositories.StationRepository;
import com.wimt.ingestion.domain.Repositories.TelemetryPingRepository;
import com.wimt.ingestion.domain.Repositories.TrainRepository;
import com.wimt.ingestion.domain.TelemetryPing;
import com.wimt.ingestion.domain.Train;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CQRS command side. Validates a ping, stores it and the OutboxEvent in ONE ACID transaction.
 * Nothing is ever published to Kafka from here: Debezium tails the WAL of outbox_events.
 */
@Service
public class TelemetryService {

    public static final String EVENT_TELEMETRY_RECEIVED = "TelemetryReceived";

    private final TrainRepository trains;
    private final StationRepository stations;
    private final TelemetryPingRepository pings;
    private final OutboxEventRepository outbox;
    private final MeterRegistry meters;

    public TelemetryService(TrainRepository trains, StationRepository stations, TelemetryPingRepository pings,
                            OutboxEventRepository outbox, MeterRegistry meters) {
        this.trains = trains;
        this.stations = stations;
        this.pings = pings;
        this.outbox = outbox;
        this.meters = meters;
    }

    @Transactional
    public TelemetryResponse ingest(TelemetryRequest req) {
        Train train = trains.findById(req.trainNumber())
                .orElseThrow(() -> new TelemetryRejectedException("UNKNOWN_TRAIN", "Unknown train " + req.trainNumber()));
        validate(req, train);

        Instant recordedAt = req.recordedAt() != null ? req.recordedAt() : Instant.now();
        double speed = req.speedKmph() != null ? req.speedKmph() : 0.0;
        String source = req.source() != null ? req.source() : "GPS";

        String dedupeKey = String.join("|", req.trainNumber(), req.journeyDate().toString(), req.eventType().name(),
                String.valueOf(req.stationCode()), recordedAt.toString(), String.valueOf(req.deviceId()));
        if (pings.existsByDedupeKey(dedupeKey)) {
            meters.counter("telemetry.duplicates").increment();
            return new TelemetryResponse(null, "DUPLICATE", null);
        }

        TelemetryPing ping = new TelemetryPing();
        ping.setTrainNumber(req.trainNumber());
        ping.setJourneyDate(req.journeyDate());
        ping.setEventType(req.eventType().name());
        ping.setStationCode(req.stationCode());
        ping.setLatitude(req.latitude());
        ping.setLongitude(req.longitude());
        ping.setSpeedKmph(speed);
        ping.setSource(source);
        ping.setDeviceId(req.deviceId());
        ping.setRecordedAt(recordedAt);
        ping.setDedupeKey(dedupeKey);
        ping = pings.save(ping);

        UUID eventId = UUID.randomUUID();
        TelemetryReceivedEvent event = new TelemetryReceivedEvent(eventId, ping.getId(), req.trainNumber(),
                req.journeyDate(), req.eventType().name(), req.stationCode(), req.latitude(), req.longitude(),
                speed, source, recordedAt);
        outbox.save(new OutboxEvent(eventId, "Train",
                Topics.trainKey(req.trainNumber(), req.journeyDate()), EVENT_TELEMETRY_RECEIVED, Json.write(event)));

        meters.counter("telemetry.accepted", "type", req.eventType().name()).increment();
        return new TelemetryResponse(ping.getId(), "ACCEPTED", eventId);
    }

    private void validate(TelemetryRequest req, Train train) {
        double lat = req.latitude();
        double lon = req.longitude();
        if (Double.isNaN(lat) || Double.isNaN(lon) || lat < 6.0 || lat > 38.0 || lon < 67.0 || lon > 98.0) {
            throw new TelemetryRejectedException("INVALID_GPS", "Coordinates are outside the Indian subcontinent: " + lat + "," + lon);
        }
        double speed = req.speedKmph() != null ? req.speedKmph() : 0.0;
        if (speed < 0 || speed > train.getMaxSpeedKmph() + 40) {
            throw new TelemetryRejectedException("INVALID_SPEED", "Speed " + speed + " km/h is impossible for train " + train.getNumber());
        }
        if (req.eventType() != EventType.POSITION) {
            if (req.stationCode() == null || req.stationCode().isBlank()) {
                throw new TelemetryRejectedException("STATION_REQUIRED", req.eventType() + " requires stationCode");
            }
        }
        if (req.stationCode() != null && !req.stationCode().isBlank() && !stations.existsById(req.stationCode())) {
            throw new TelemetryRejectedException("UNKNOWN_STATION", "Unknown station " + req.stationCode());
        }
        String day = req.journeyDate().getDayOfWeek().name().substring(0, 3);
        if (!Arrays.asList(train.getRunsOn().split(",")).contains(day)) {
            throw new TelemetryRejectedException("TRAIN_NOT_RUNNING", "Train " + train.getNumber() + " does not run on " + day);
        }
        if (req.recordedAt() != null && Duration.between(req.recordedAt(), Instant.now()).toDays() > 2) {
            throw new TelemetryRejectedException("STALE_TELEMETRY", "recordedAt is older than 2 days");
        }
    }
}
