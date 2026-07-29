package com.sion.pos.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO processed_event (consumer, event_id, created_at)
            VALUES (:consumer, :eventId, NOW(6))
            """, nativeQuery = true)
    int record(@Param("consumer") String consumer, @Param("eventId") String eventId);
}