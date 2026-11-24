package com.wimt.eta.api;

import com.wimt.eta.domain.EtaProjection;
import com.wimt.eta.domain.EtaProjectionRepository;
import com.wimt.eta.domain.TrainState;
import com.wimt.eta.domain.TrainStateRepository;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operational read-only view of the write-side aggregate (diagnostics; user reads go to train-query-service). */
@RestController
@RequestMapping("/api/v1/eta")
public class EtaController {
    private final TrainStateRepository states;
    private final EtaProjectionRepository projections;

    public EtaController(TrainStateRepository states, EtaProjectionRepository projections) {
        this.states = states;
        this.projections = projections;
    }

    @GetMapping("/{trainNumber}/{journeyDate}/state")
    public ResponseEntity<TrainState> state(@PathVariable String trainNumber, @PathVariable LocalDate journeyDate) {
        return states.findByTrainNumberAndJourneyDate(trainNumber, journeyDate)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{trainNumber}/{journeyDate}/latest-projection")
    public ResponseEntity<EtaProjection> latest(@PathVariable String trainNumber, @PathVariable LocalDate journeyDate) {
        return projections.findFirstByTrainNumberAndJourneyDateOrderByIdDesc(trainNumber, journeyDate)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
