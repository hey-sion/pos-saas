package com.sion.pos.application.supply;

import java.util.List;

public record SupplyOrderCreateCommand(
        Long storeId,
        List<SupplyOrderLine> items
) {
}