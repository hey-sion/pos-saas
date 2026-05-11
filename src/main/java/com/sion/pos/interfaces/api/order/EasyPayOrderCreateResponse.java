package com.sion.pos.interfaces.api.order;

import com.sion.pos.application.order.EasyPayOrderCreateInfo;

public record EasyPayOrderCreateResponse(
        OrderResult order,
        PaymentResult payment,
        PortOneRequestParams portOne
) {

    public static EasyPayOrderCreateResponse from(EasyPayOrderCreateInfo info) {
        return new EasyPayOrderCreateResponse(
                new OrderResult(info.orderId(), info.orderNumber(), info.totalAmount()),
                new PaymentResult(info.paymentId(), info.pgPaymentId()),
                new PortOneRequestParams(
                        info.portOneStoreId(),
                        info.channelKey(),
                        info.pgPaymentId(),
                        info.orderName(),
                        info.totalAmount(),
                        "EASY_PAY",
                        new PortOneRequestParams.EasyPay(info.provider().toPortOneCode())
                )
        );
    }

    public record OrderResult(Long id, Integer orderNumber, Integer totalAmount) {}

    public record PaymentResult(Long id, String pgPaymentId) {}

    public record PortOneRequestParams(
            String storeId,
            String channelKey,
            String paymentId,
            String orderName,
            Integer totalAmount,
            String payMethod,
            EasyPay easyPay
    ) {
        public record EasyPay(String easyPayProvider) {}
    }
}