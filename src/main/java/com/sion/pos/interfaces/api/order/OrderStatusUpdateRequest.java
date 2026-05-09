package com.sion.pos.interfaces.api.order;

import com.sion.pos.domain.order.Order;
import jakarta.validation.constraints.NotNull;

public record OrderStatusUpdateRequest(
        @NotNull(message = "status는 필수입니다.")
        Order.Status status
) {
}