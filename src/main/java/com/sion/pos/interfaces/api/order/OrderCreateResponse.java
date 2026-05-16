package com.sion.pos.interfaces.api.order;

import com.sion.pos.domain.order.Order;
import java.time.LocalDate;

public record OrderCreateResponse(
        Long id,
        Long storeId,
        LocalDate orderDate,
        Integer orderNumber,
        Order.Status status,
        Integer totalAmount
) {

    public static OrderCreateResponse from(Order order) {
        return new OrderCreateResponse(
                order.getId(),
                order.getStoreId(),
                order.getOrderDate(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount()
        );
    }
}