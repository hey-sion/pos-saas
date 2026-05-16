package com.sion.pos.application.order;

public record OrderItemLine(
        Long menuId,
        Integer quantity
) {
}
