package com.sion.pos.interfaces.api.supply;

import com.sion.pos.application.supply.SupplyOrderInfo;
import java.time.LocalDateTime;
import java.util.List;

public record SupplyOrderResponse(
        Long id,
        String status,
        Integer totalAmount,
        LocalDateTime createdAt,
        List<Item> items
) {

    public static SupplyOrderResponse from(SupplyOrderInfo info) {
        return new SupplyOrderResponse(
                info.id(),
                info.status(),
                info.totalAmount(),
                info.createdAt(),
                info.items().stream().map(Item::from).toList()
        );
    }

    public record Item(
            String itemCode,
            String itemName,
            String unit,
            Integer quantity,
            Integer unitPrice
    ) {

        private static Item from(SupplyOrderInfo.Item item) {
            return new Item(
                    item.itemCode(),
                    item.itemName(),
                    item.unit(),
                    item.quantity(),
                    item.unitPrice());
        }
    }
}