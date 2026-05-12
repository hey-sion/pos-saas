package com.sion.pos.interfaces.api.payment;

import com.sion.pos.application.payment.PaymentCreateCommand;
import com.sion.pos.domain.payment.Payment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentCreateRequest(
        @NotNull(message = "주문 정보가 필요합니다.")
        @Positive(message = "주문 정보가 올바르지 않습니다.")
        Long orderId,

        @NotNull(message = "결제 수단을 선택해주세요.")
        Payment.Method method,

        Payment.Provider provider
) {

    public PaymentCreateCommand toCommand() {
        return new PaymentCreateCommand(orderId, method, provider);
    }
}