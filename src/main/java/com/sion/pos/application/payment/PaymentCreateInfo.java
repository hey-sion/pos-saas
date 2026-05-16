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
    private static final String CURRENCY_KRW = "KRW";
    private static final String PAY_METHOD_EASY_PAY = "EASY_PAY";

    public static PaymentCreateInfo offline(Payment payment) {
        return new PaymentCreateInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmount(),
                null
        );
    }

    public static PaymentCreateInfo pg(Payment payment, String storeId, String channelKey, String orderName) {
        return new PaymentCreateInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmount(),
                new PgRequestParams(
                        payment.getPgPaymentId(),
                        storeId,
                        channelKey,
                        orderName,
                        payment.getAmount(),
                        PAY_METHOD_EASY_PAY,
                        new PgRequestParams.EasyPay(payment.getProvider().toPortOneCode()),
                        CURRENCY_KRW
                )
        );
    }

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