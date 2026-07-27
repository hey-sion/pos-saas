package com.sion.pos.domain.event;

import com.sion.pos.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox extends BaseEntity {

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public static Outbox create(String eventType, String payload) {
        Outbox outbox = new Outbox();
        outbox.eventType = eventType;
        outbox.payload = payload;
        return outbox;
    }

    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
    }
}