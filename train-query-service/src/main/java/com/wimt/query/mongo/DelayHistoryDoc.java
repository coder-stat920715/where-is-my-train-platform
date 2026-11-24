package com.wimt.query.mongo;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Historical delay projection point (collection delay_history). */
@Document("delay_history")
public record DelayHistoryDoc(
        @Id String id,
        String trainNumber,
        String journeyDate,
        String stationCode,
        String eventType,
        int delayMinutes,
        Instant recordedAt) {
}
