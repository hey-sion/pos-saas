package com.sion.pos.application.order;

import com.sion.pos.domain.payment.Payment;

public record EasyPayOrderCreateInfo(
        Long orderId,
        Integer orderNumber,
        Integer totalAmount,
        Long paymentId,
        String pgPaymentId,
        Payment.Provider provider,
        String orderName,
        String portOneStoreId,
        String channelKey) {
}