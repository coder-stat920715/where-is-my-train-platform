package com.wimt.eta.domain;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainStateRepository extends JpaRepository<TrainState, Long> {
    Optional<TrainState> findByTrainNumberAndJourneyDate(String trainNumber, LocalDate journeyDate);
}
