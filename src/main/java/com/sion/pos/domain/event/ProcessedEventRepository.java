package com.sion.pos.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    @Modifying
    @Query(value = "INSERT IGNORE INTO processed_event (event_id, created_at) VALUES (:eventId, NOW(6))",
            nativeQuery = true)
    int record(@Param("eventId") String eventId);
}