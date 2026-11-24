package com.wimt.ingestion.api;

import com.wimt.ingestion.domain.Repositories.TelemetryPingRepository;
import com.wimt.ingestion.domain.TelemetryPing;
import com.wimt.ingestion.service.TelemetryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final TelemetryService service;
    private final TelemetryPingRepository pings;

    public TelemetryController(TelemetryService service, TelemetryPingRepository pings) {
        this.service = service;
        this.pings = pings;
    }

    @PostMapping
    public ResponseEntity<TelemetryResponse> ingest(@Valid @RequestBody TelemetryRequest request) {
        TelemetryResponse response = service.ingest(request);
        return ResponseEntity.status("DUPLICATE".equals(response.status()) ? HttpStatus.OK : HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/recent")
    public List<TelemetryPing> recent(@RequestParam String trainNumber, @RequestParam LocalDate journeyDate) {
        return pings.findTop20ByTrainNumberAndJourneyDateOrderByIdDesc(trainNumber, journeyDate);
    }
}
