package com.wimt.query.service;

import com.wimt.common.Json;
import com.wimt.query.model.BoardEntry;
import com.wimt.query.model.LiveDiff;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Pushes live diffs to WebSocket (STOMP) topics and SSE subscribers. */
@Component
public class LiveBroadcaster {
    private final SimpMessagingTemplate ws;
    private final SseHub sse;

    public LiveBroadcaster(SimpMessagingTemplate ws, SseHub sse) {
        this.ws = ws;
        this.sse = sse;
    }

    public void train(LiveDiff diff) {
        String json = Json.write(diff);
        ws.convertAndSend("/topic/trains/" + diff.trainNumber() + "/" + diff.journeyDate(), json);
        sse.send(SseHub.keyFor(diff.trainNumber(), diff.journeyDate()), "live-update", json);
    }

    public void station(BoardEntry entry) {
        ws.convertAndSend("/topic/stations/" + entry.stationCode(), Json.write(entry));
    }
}
