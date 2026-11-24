package com.wimt.query.mongo;

import com.wimt.query.model.ServiceDegradedException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/** MongoDB read side (route search, delay history), protected by Resilience4j. */
@Component
public class RouteReadModel {
    private static final Logger log = LoggerFactory.getLogger(RouteReadModel.class);

    private final MongoTemplate mongo;

    public RouteReadModel(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @CircuitBreaker(name = "mongoRead", fallbackMethod = "routesFallback")
    @Retry(name = "mongoRead")
    @Bulkhead(name = "mongoRead")
    public List<TrainRouteDoc> findRoutesBetween(String from, String to) {
        return mongo.find(new Query(Criteria.where("stops.code").all(from, to)), TrainRouteDoc.class);
    }

    /** Degraded mode: the timetable is static reference data, so serve it from the in-process seed copy. */
    public List<TrainRouteDoc> routesFallback(String from, String to, Throwable t) {
        log.warn("Mongo route search degraded ({}), serving in-memory timetable", t.toString());
        return RouteSeedData.routes().stream()
                .filter(r -> r.stops().stream().anyMatch(s -> s.code().equals(from))
                        && r.stops().stream().anyMatch(s -> s.code().equals(to)))
                .toList();
    }

    @CircuitBreaker(name = "mongoRead", fallbackMethod = "historyFallback")
    @Retry(name = "mongoRead")
    @Bulkhead(name = "mongoRead")
    public List<DelayHistoryDoc> delayHistory(String trainNumber, String journeyDate) {
        Query q = new Query(Criteria.where("trainNumber").is(trainNumber).and("journeyDate").is(journeyDate))
                .with(Sort.by(Sort.Direction.ASC, "recordedAt"));
        return mongo.find(q, DelayHistoryDoc.class);
    }

    public List<DelayHistoryDoc> historyFallback(String trainNumber, String journeyDate, Throwable t) {
        throw new ServiceDegradedException("Delay history store unavailable", t);
    }
}
