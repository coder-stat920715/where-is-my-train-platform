package com.wimt.eta.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopActualRepository extends JpaRepository<StopActual, Long> {
    List<StopActual> findByTrainNumberAndJourneyDate(String trainNumber, LocalDate journeyDate);

    Optional<StopActual> findByTrainNumberAndJourneyDateAndSeq(String trainNumber, LocalDate journeyDate, int seq);
}
