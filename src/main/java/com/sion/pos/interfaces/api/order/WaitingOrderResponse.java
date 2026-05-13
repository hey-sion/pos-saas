package com.sion.pos.interfaces.api.order;

import com.sion.pos.application.order.WaitingOrderInfo;
import java.util.List;

public record WaitingOrderResponse(
        Long id,
        Integer orderNumber,
        String status,
        List<Item> items,
        String paymentMethod,
        Integer totalAmount
) {

    public static WaitingOrderResponse from(WaitingOrderInfo info) {
        return new WaitingOrderResponse(
                info.id(),
                info.orderNumber(),
                info.status(),
                info.items().stream().map(Item::from).toList(),
                info.paymentMethod(),
                info.totalAmount()
        );
    }

    public record Item(
            Long menuId,
            String menuName,
            Integer price,
            Integer quantity
    ) {

        private static Item from(WaitingOrderInfo.Item item) {
            return new Item(item.menuId(), item.menuName(), item.price(), item.quantity());
        }
    }
}