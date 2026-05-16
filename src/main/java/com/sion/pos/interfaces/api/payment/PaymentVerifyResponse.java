package com.sion.pos.interfaces.api.payment;

import com.sion.pos.application.payment.PaymentVerifyInfo;
import com.sion.pos.domain.payment.Payment;
import java.time.LocalDateTime;

public record PaymentVerifyResponse(
        Long paymentId,
        Long orderId,
        Payment.Status status,
        Integer amount,
        LocalDateTime paidAt,
        String failReason
) {

    public static PaymentVerifyResponse from(PaymentVerifyInfo info) {
        return new PaymentVerifyResponse(
                info.paymentId(),
                info.orderId(),
                info.status(),
                info.amount(),
                info.paidAt(),
                info.failReason()
        );
    }
}