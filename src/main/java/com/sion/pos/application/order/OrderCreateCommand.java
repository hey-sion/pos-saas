package com.sion.pos.application.order;

import com.sion.pos.domain.payment.Payment;
import java.util.List;

public record OrderCreateCommand(
        Long storeId,
        List<Line> items,
        Payment.Method method) {

    public record Line(Long menuId, Integer quantity) {}
}