package com.sion.pos.application.order;

import java.util.List;

public record OrderUpdateItemsCommand(
        List<OrderItemLine> items
) {
}
