package com.wimt.query.api;

import com.wimt.common.Json;
import com.wimt.query.model.TrainLiveView;
import com.wimt.query.service.QueryService;
import com.wimt.query.service.SseHub;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Server-Sent Events alternative to WebSocket: GET /api/v1/stream/trains/{no}/{date}. */
@RestController
@RequestMapping("/api/v1/stream")
public class StreamController {
    private final SseHub hub;
    private final QueryService service;

    public StreamController(SseHub hub, QueryService service) {
        this.hub = hub;
        this.service = service;
    }

    @GetMapping(value = "/trains/{trainNumber}/{journeyDate}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String trainNumber,
                             @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate) {
        SseEmitter emitter = hub.subscribe(SseHub.keyFor(trainNumber, journeyDate));
        try {
            Optional<TrainLiveView> snapshot = service.runningStatus(trainNumber, journeyDate);
            if (snapshot.isPresent()) {
                emitter.send(SseEmitter.event().name("snapshot").data(Json.write(snapshot.get())));
            }
        } catch (IOException | RuntimeException e) {
            // snapshot is best effort; live updates still flow
        }
        return emitter;
    }
}
