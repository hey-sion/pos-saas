package com.sion.pos.application.supply;

import java.time.LocalDateTime;
import java.util.List;

public record SupplyOrderInfo(
        Long id,
        String status,
        Integer totalAmount,
        LocalDateTime createdAt,
        List<Item> items
) {

    public record Item(
            String itemCode,
            String itemName,
            String unit,
            Integer quantity,
            Integer unitPrice
    ) {
    }
}