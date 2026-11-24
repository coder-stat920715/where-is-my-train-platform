package com.wimt.query.store;

import com.wimt.common.Json;
import com.wimt.query.model.BoardEntry;
import com.wimt.query.model.TrainLiveView;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis write model of the query side (projections + idempotency set). Reads live in LiveReadModel. */
@Component
public class ViewStore {
    private static final Duration VIEW_TTL = Duration.ofDays(3);

    private final StringRedisTemplate redis;

    public ViewStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<TrainLiveView> peekLive(String trainNumber, LocalDate date) {
        String json = redis.opsForValue().get(Keys.live(trainNumber, date));
        return json == null ? Optional.empty() : Optional.of(Json.read(json, TrainLiveView.class));
    }

    public void saveLive(TrainLiveView view) {
        redis.opsForValue().set(Keys.live(view.trainNumber(), view.journeyDate()), Json.write(view), VIEW_TTL);
    }

    public boolean liveExists(String trainNumber, LocalDate date) {
        return Boolean.TRUE.equals(redis.hasKey(Keys.live(trainNumber, date)));
    }

    public void upsertBoard(BoardEntry entry) {
        String key = Keys.board(entry.stationCode());
        redis.opsForHash().put(key, entry.field(), Json.write(entry));
        redis.expire(key, VIEW_TTL);
    }

    public void putBoardIfAbsent(BoardEntry entry) {
        String key = Keys.board(entry.stationCode());
        redis.opsForHash().putIfAbsent(key, entry.field(), Json.write(entry));
        redis.expire(key, VIEW_TTL);
    }

    /** Platform clock = newest telemetry timestamp seen; lets boards work with simulated time. */
    public void touchClock(Instant recordedAt) {
        String current = redis.opsForValue().get(Keys.PLATFORM_CLOCK);
        if (current == null || Instant.parse(current).isBefore(recordedAt)) {
            redis.opsForValue().set(Keys.PLATFORM_CLOCK, recordedAt.toString(), Duration.ofHours(6));
        }
    }

    // ---- idempotent consumption: two rotating daily Redis sets act as the processed_events dedup table
    public boolean markIfNew(UUID eventId) {
        long today = LocalDate.now().toEpochDay();
        String id = eventId.toString();
        if (Boolean.TRUE.equals(redis.opsForSet().isMember(Keys.PROCESSED_EVENTS + ":" + (today - 1), id))) {
            return false;
        }
        String key = Keys.PROCESSED_EVENTS + ":" + today;
        Long added = redis.opsForSet().add(key, id);
        redis.expire(key, Duration.ofHours(48));
        return added != null && added > 0;
    }

    public void unmark(UUID eventId) {
        redis.opsForSet().remove(Keys.PROCESSED_EVENTS + ":" + LocalDate.now().toEpochDay(), eventId.toString());
    }
}
