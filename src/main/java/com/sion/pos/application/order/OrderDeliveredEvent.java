package com.sion.pos.application.order;

import java.time.LocalDateTime;

public record OrderDeliveredEvent(
        String eventId,
        Long storeId,
        LocalDateTime occurredAt,
        int totalAmount
) {
}