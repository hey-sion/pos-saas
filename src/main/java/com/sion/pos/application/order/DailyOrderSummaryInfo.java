package com.sion.pos.application.order;

import java.time.LocalDate;
import java.util.List;

public record DailyOrderSummaryInfo(
        LocalDate date,
        Integer salesAmount,
        Integer salesOrderCount,
        Integer totalOrderCount,
        List<OrderInfo> orders
) {

    public record OrderInfo(
            Long id,
            Integer orderNumber,
            String status,
            List<Item> items,
            String paymentMethod,
            Integer totalAmount
    ) {
    }

    public record Item(
            String menuName,
            Integer quantity
    ) {
    }
}