package com.wimt.ingestion.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class Repositories {
    private Repositories() {}

    public interface StationRepository extends JpaRepository<Station, String> {}

    public interface TrainRepository extends JpaRepository<Train, String> {}

    public interface TrainScheduleRepository extends JpaRepository<TrainSchedule, Long> {
        List<TrainSchedule> findByTrainNumberOrderBySeqAsc(String trainNumber);
    }

    public interface TelemetryPingRepository extends JpaRepository<TelemetryPing, Long> {
        boolean existsByDedupeKey(String dedupeKey);

        List<TelemetryPing> findTop20ByTrainNumberAndJourneyDateOrderByIdDesc(String trainNumber, java.time.LocalDate journeyDate);

        @Modifying
        @Query("update TelemetryPing p set p.anomaly = true, p.anomalyReason = :reason where p.id = :id")
        int markAnomaly(@Param("id") long id, @Param("reason") String reason);
    }

    public interface OutboxEventRepository extends JpaRepository<OutboxEvent, java.util.UUID> {
        @Modifying
        @Query("delete from OutboxEvent o where o.createdAt < :cutoff")
        int purgeOlderThan(@Param("cutoff") Instant cutoff);
    }
}
