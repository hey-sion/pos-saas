package com.sion.pos.interfaces.api.supply;

import com.sion.pos.domain.supply.SupplyOrder;
import java.time.LocalDateTime;

public record SupplyOrderCreateResponse(
        Long id,
        Long storeId,
        SupplyOrder.Status status,
        Integer totalAmount,
        LocalDateTime createdAt
) {

    public static SupplyOrderCreateResponse from(SupplyOrder order) {
        return new SupplyOrderCreateResponse(
                order.getId(),
                order.getStoreId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}