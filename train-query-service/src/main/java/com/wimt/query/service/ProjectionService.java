package com.wimt.query.service;

import com.wimt.common.EtaRecalculatedEvent;
import com.wimt.common.StopProjection;
import com.wimt.query.model.BoardEntry;
import com.wimt.query.model.LiveDiff;
import com.wimt.query.model.TrainLiveView;
import com.wimt.query.mongo.DelayHistoryDoc;
import com.wimt.query.store.ViewStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * CQRS query side: turns an EtaRecalculated projection into denormalized read models
 * (Redis live view + station boards, MongoDB delay history) and broadcasts the live diff.
 */
@Service
public class ProjectionService {

    private final ViewStore store;
    private final MongoTemplate mongo;
    private final LiveBroadcaster broadcaster;

    public ProjectionService(ViewStore store, MongoTemplate mongo, LiveBroadcaster broadcaster) {
        this.store = store;
        this.mongo = mongo;
        this.broadcaster = broadcaster;
    }

    public void apply(EtaRecalculatedEvent eta) {
        if (eta == null || eta.stops() == null || eta.stops().isEmpty()) {
            throw new IllegalArgumentException("EtaRecalculated event without stops");
        }
        Optional<TrainLiveView> previous = store.peekLive(eta.trainNumber(), eta.journeyDate());
        List<StopProjection> changed = changedStops(previous, eta.stops());

        // 1. live train document
        store.saveLive(TrainLiveView.from(eta));

        // 2. station boards (one hash field per train/journey, for every stop of the route)
        List<BoardEntry> changedBoards = new ArrayList<>();
        for (StopProjection stop : eta.stops()) {
            BoardEntry entry = BoardEntry.from(eta, stop);
            store.upsertBoard(entry);
            if (changed.stream().anyMatch(c -> c.seq() == stop.seq())) {
                changedBoards.add(entry);
            }
        }

        // 3. historical delay projection (only for real station events, not every GPS ping)
        if (!"POSITION".equals(eta.triggerType())) {
            mongo.insert(new DelayHistoryDoc(null, eta.trainNumber(), eta.journeyDate().toString(),
                    eta.lastCrossedStation(), eta.triggerType(), eta.delayMinutes(), eta.recordedAt()));
        }

        // 4. platform clock + real-time push
        store.touchClock(eta.recordedAt());
        LiveDiff diff = new LiveDiff("LIVE_UPDATE", eta.trainNumber(), eta.journeyDate(), eta.status(),
                eta.delayMinutes(), previous.map(TrainLiveView::delayMinutes).orElse(null), eta.lastCrossedStation(),
                eta.nextStation(), eta.latitude(), eta.longitude(), eta.speedKmph(), eta.destinationEta(),
                eta.recordedAt(), changed);
        broadcaster.train(diff);
        changedBoards.forEach(broadcaster::station);
    }

    private static List<StopProjection> changedStops(Optional<TrainLiveView> previous, List<StopProjection> current) {
        if (previous.isEmpty()) {
            return current;
        }
        Map<Integer, StopProjection> before = new HashMap<>();
        previous.get().stopsInOrder().forEach(s -> before.put(s.seq(), s));
        List<StopProjection> changed = new ArrayList<>();
        for (StopProjection now : current) {
            StopProjection old = before.get(now.seq());
            if (old == null || !Objects.equals(old.expectedArrival(), now.expectedArrival())
                    || !Objects.equals(old.expectedDeparture(), now.expectedDeparture())
                    || !Objects.equals(old.actualArrival(), now.actualArrival())
                    || !Objects.equals(old.actualDeparture(), now.actualDeparture())
                    || old.crossed() != now.crossed()) {
                changed.add(now);
            }
        }
        return changed;
    }
}
