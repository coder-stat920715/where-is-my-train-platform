package com.wimt.saga.domain;

import com.wimt.common.SagaStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {
    boolean existsByTriggerEventId(UUID triggerEventId);

    List<SagaInstance> findByStateInAndUpdatedAtBefore(Collection<SagaStatus> states, Instant cutoff);

    List<SagaInstance> findTop50ByOrderByCreatedAtDesc();

    List<SagaInstance> findTop50ByStateOrderByCreatedAtDesc(SagaStatus state);

    @Query("select s.state, count(s) from SagaInstance s group by s.state")
    List<Object[]> countByState();
}
