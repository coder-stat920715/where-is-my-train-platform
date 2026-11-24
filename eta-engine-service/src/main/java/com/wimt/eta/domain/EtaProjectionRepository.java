package com.wimt.eta.domain;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtaProjectionRepository extends JpaRepository<EtaProjection, Long> {
    Optional<EtaProjection> findFirstByTrainNumberAndJourneyDateOrderByIdDesc(String trainNumber, LocalDate journeyDate);
}
