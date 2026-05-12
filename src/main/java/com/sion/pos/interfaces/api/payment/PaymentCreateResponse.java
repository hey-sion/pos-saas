package com.sion.pos.interfaces.api.payment;

import com.sion.pos.application.payment.PaymentCreateInfo;
import com.sion.pos.domain.payment.Payment;

public record PaymentCreateResponse(
        PaymentResult payment,
        PortOneRequestParams pg
) {

    public static PaymentCreateResponse from(PaymentCreateInfo info) {
        return new PaymentCreateResponse(
                new PaymentResult(info.paymentId(), info.orderId(), info.method(), info.status(), info.amount()),
                info.pg() == null ? null : PortOneRequestParams.from(info.pg())
        );
    }

    public record PaymentResult(
            Long id,
            Long orderId,
            Payment.Method method,
            Payment.Status status,
            Integer amount
    ) {}

    public record PortOneRequestParams(
            String paymentId,
            String storeId,
            String channelKey,
            String orderName,
            Integer totalAmount,
            String payMethod,
            EasyPay easyPay,
            String currency
    ) {

        public static PortOneRequestParams from(PaymentCreateInfo.PgRequestParams pg) {
            return new PortOneRequestParams(
                    pg.paymentId(),
                    pg.storeId(),
                    pg.channelKey(),
                    pg.orderName(),
                    pg.totalAmount(),
                    pg.payMethod(),
                    new EasyPay(pg.easyPay().easyPayProvider()),
                    pg.currency()
            );
        }

        public record EasyPay(String easyPayProvider) {}
    }
}