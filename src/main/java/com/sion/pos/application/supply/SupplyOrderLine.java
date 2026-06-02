package com.sion.pos.application.supply;

public record SupplyOrderLine(
        String itemCode,
        Integer quantity
) {
}