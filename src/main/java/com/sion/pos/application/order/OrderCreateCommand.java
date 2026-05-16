package com.sion.pos.application.order;

import java.util.List;

public record OrderCreateCommand(
        Long storeId,
        List<OrderItemLine> items) {
}
