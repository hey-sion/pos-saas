package com.sion.pos.application.order;

import com.sion.pos.application.event.EventPayload;
import java.time.LocalDateTime;

public record OrderDeliveredEvent(
        String eventId,
        Long storeId,
        LocalDateTime occurredAt,
        int totalAmount
) implements EventPayload {
}