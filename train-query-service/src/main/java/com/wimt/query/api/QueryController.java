package com.wimt.query.api;

import com.wimt.query.model.StationBoard;
import com.wimt.query.model.TrainLiveView;
import com.wimt.query.model.TrainSearchResult;
import com.wimt.query.mongo.DelayHistoryDoc;
import com.wimt.query.service.QueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CQRS read API. Never writes. */
@RestController
@RequestMapping("/api/v1/query")
public class QueryController {
    private final QueryService service;

    public QueryController(QueryService service) {
        this.service = service;
    }

    /** Live Train Running Status: location, crossed/upcoming stations, delay and ETA to destination. */
    @GetMapping("/trains/{trainNumber}/running-status")
    public ResponseEntity<TrainLiveView> runningStatus(
            @PathVariable String trainNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate) {
        LocalDate date = journeyDate != null ? journeyDate : LocalDate.now(QueryService.IST);
        return service.runningStatus(trainNumber, date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Station-to-station search with live delay status. */
    @GetMapping("/trains/between")
    public List<TrainSearchResult> between(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.between(from, to, date != null ? date : LocalDate.now(QueryService.IST));
    }

    /** Live station departure board (default: next 4 hours). */
    @GetMapping("/stations/{stationCode}/board")
    public StationBoard board(
            @PathVariable String stationCode,
            @RequestParam(defaultValue = "4") int hours,
            @RequestParam(required = false) Instant asOf) {
        return service.board(stationCode, Math.max(1, Math.min(hours, 24)), asOf);
    }

    @GetMapping("/trains/{trainNumber}/delay-history")
    public List<DelayHistoryDoc> delayHistory(
            @PathVariable String trainNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate) {
        return service.delayHistory(trainNumber, journeyDate != null ? journeyDate : LocalDate.now(QueryService.IST));
    }
}
