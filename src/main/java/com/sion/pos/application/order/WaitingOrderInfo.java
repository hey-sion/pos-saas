package com.sion.pos.application.order;

import java.util.List;

public record WaitingOrderInfo(
        Long id,
        Integer orderNumber,
        String status,
        List<Item> items,
        String paymentMethod,
        Integer totalAmount
) {

    public record Item(
            String menuName,
            Integer quantity
    ) {
    }
}