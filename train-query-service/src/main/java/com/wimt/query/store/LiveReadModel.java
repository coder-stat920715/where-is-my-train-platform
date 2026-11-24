package com.wimt.query.store;

import com.wimt.common.Json;
import com.wimt.query.model.BoardEntry;
import com.wimt.query.model.ServiceDegradedException;
import com.wimt.query.model.TrainLiveView;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis read side (sub-10 ms lookups). Every call is protected by Resilience4j
 * {@code @CircuitBreaker}, {@code @Retry} and {@code @Bulkhead}; when Redis is unhealthy the fallback serves the
 * last value this instance saw (flagged stale) or fails with a ServiceDegradedException (HTTP 503).
 */
@Component
public class LiveReadModel {
    private static final Logger log = LoggerFactory.getLogger(LiveReadModel.class);

    private final StringRedisTemplate redis;
    private final Map<String, TrainLiveView> lastKnownLive = new ConcurrentHashMap<>();
    private final Map<String, List<BoardEntry>> lastKnownBoard = new ConcurrentHashMap<>();

    public LiveReadModel(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @CircuitBreaker(name = "redisRead", fallbackMethod = "liveFallback")
    @Retry(name = "redisRead")
    @Bulkhead(name = "redisRead")
    public Optional<TrainLiveView> getLive(String trainNumber, LocalDate date) {
        String key = Keys.live(trainNumber, date);
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        TrainLiveView view = Json.read(json, TrainLiveView.class);
        lastKnownLive.put(key, view);
        return Optional.of(view);
    }

    public Optional<TrainLiveView> liveFallback(String trainNumber, LocalDate date, Throwable t) {
        log.warn("Redis live read degraded for {} {}: {}", trainNumber, date, t.toString());
        TrainLiveView cached = lastKnownLive.get(Keys.live(trainNumber, date));
        if (cached != null) {
            return Optional.of(cached.asStale());
        }
        throw new ServiceDegradedException("Live status store unavailable", t);
    }

    @CircuitBreaker(name = "redisRead", fallbackMethod = "boardFallback")
    @Retry(name = "redisRead")
    @Bulkhead(name = "redisRead")
    public List<BoardEntry> getBoard(String stationCode) {
        Map<Object, Object> raw = redis.opsForHash().entries(Keys.board(stationCode));
        List<BoardEntry> entries = new ArrayList<>(raw.size());
        for (Object v : raw.values()) {
            entries.add(Json.read(String.valueOf(v), BoardEntry.class));
        }
        lastKnownBoard.put(stationCode, entries);
        return entries;
    }

    public List<BoardEntry> boardFallback(String stationCode, Throwable t) {
        log.warn("Redis board read degraded for {}: {}", stationCode, t.toString());
        List<BoardEntry> cached = lastKnownBoard.get(stationCode);
        if (cached != null) {
            return cached;
        }
        throw new ServiceDegradedException("Station board store unavailable", t);
    }

    /** Whether the last answer for a station came from the local fallback cache is not tracked; callers use getBoard. */
    public Optional<Instant> platformClock() {
        try {
            String v = redis.opsForValue().get(Keys.PLATFORM_CLOCK);
            return v == null ? Optional.empty() : Optional.of(Instant.parse(v));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
