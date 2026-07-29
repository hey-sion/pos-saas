package com.sion.pos.domain.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processed_event")
@IdClass(ProcessedEventId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @Column(name = "consumer")
    private String consumer;

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}