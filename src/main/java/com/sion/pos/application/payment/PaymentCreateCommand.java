package com.sion.pos.application.payment;

import com.sion.pos.domain.payment.Payment;

public record PaymentCreateCommand(
        Long orderId,
        Payment.Method method,
        Payment.Provider provider
) {
}