package com.wimt.query.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Registry of Server-Sent-Event subscribers per train journey. */
@Component
public class SseHub {
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String key) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        List<SseEmitter> list = emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));
        return emitter;
    }

    public void send(String key, String eventName, String json) {
        List<SseEmitter> list = emitters.get(key);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);
            }
        }
    }

    public static String keyFor(String trainNumber, Object journeyDate) {
        return trainNumber + ":" + journeyDate;
    }
}
