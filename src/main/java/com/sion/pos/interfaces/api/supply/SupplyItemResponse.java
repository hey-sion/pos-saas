package com.sion.pos.interfaces.api.supply;

import com.sion.pos.domain.supply.SupplyItem;

public record SupplyItemResponse(
        String code,
        String name,
        String unit,
        Integer unitPrice,
        Integer packSize,
        String packUnit
) {

    public static SupplyItemResponse from(SupplyItem item) {
        return new SupplyItemResponse(
                item.name(),
                item.getItemName(),
                item.getUnit(),
                item.getUnitPrice(),
                item.getPackSize(),
                item.getPackUnit()
        );
    }
}