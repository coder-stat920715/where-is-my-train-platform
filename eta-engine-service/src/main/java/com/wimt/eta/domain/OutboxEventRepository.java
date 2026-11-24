package com.wimt.eta.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Modifying
    @Query("delete from OutboxEvent o where o.createdAt < :cutoff")
    int purgeOlderThan(@Param("cutoff") Instant cutoff);
}
