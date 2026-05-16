package com.sion.pos.interfaces.api.order;

import com.sion.pos.application.order.DailyOrderSummaryInfo;
import java.time.LocalDate;
import java.util.List;

public record DailyOrderSummaryResponse(
        LocalDate date,
        Integer salesAmount,
        Integer salesOrderCount,
        Integer totalOrderCount,
        List<OrderResponse> orders
) {

    public static DailyOrderSummaryResponse from(DailyOrderSummaryInfo info) {
        return new DailyOrderSummaryResponse(
                info.date(),
                info.salesAmount(),
                info.salesOrderCount(),
                info.totalOrderCount(),
                info.orders().stream().map(OrderResponse::from).toList()
        );
    }

    public record OrderResponse(
            Long id,
            Integer orderNumber,
            String status,
            List<Item> items,
            String paymentMethod,
            Integer totalAmount
    ) {

        private static OrderResponse from(DailyOrderSummaryInfo.OrderInfo order) {
            return new OrderResponse(
                    order.id(),
                    order.orderNumber(),
                    order.status(),
                    order.items().stream().map(Item::from).toList(),
                    order.paymentMethod(),
                    order.totalAmount()
            );
        }
    }

    public record Item(
            String menuName,
            Integer quantity
    ) {

        private static Item from(DailyOrderSummaryInfo.Item item) {
            return new Item(item.menuName(), item.quantity());
        }
    }
}