package com.sion.pos.application.payment;

import com.sion.pos.domain.payment.Payment;

public record PaymentCreateInfo(
        Long paymentId,
        Long orderId,
        Payment.Method method,
        Payment.Status status,
        Integer amount,
        PgRequestParams pg
) {

    public record PgRequestParams(
            String paymentId,
            String storeId,
            String channelKey,
            String orderName,
            Integer totalAmount,
            String payMethod,
            EasyPay easyPay,
            String currency
    ) {

        public record EasyPay(String easyPayProvider) {}
    }
}