package com.sion.pos.application.order;

import com.sion.pos.domain.payment.Payment;
import java.util.List;

public record EasyPayOrderCreateCommand(
        Long storeId,
        List<Line> items,
        Payment.Provider provider) {

    public record Line(Long menuId, Integer quantity) {}
}