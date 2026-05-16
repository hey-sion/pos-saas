package com.sion.pos.application.payment;

import com.sion.pos.domain.payment.Payment;
import java.time.LocalDateTime;

public record PaymentVerifyInfo(
        Long paymentId,
        Long orderId,
        Payment.Status status,
        Integer amount,
        LocalDateTime paidAt,
        String failReason
) {

    public static PaymentVerifyInfo from(Payment payment) {
        return new PaymentVerifyInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getPaidAt(),
                payment.getFailReason()
        );
    }
}