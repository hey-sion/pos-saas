package com.sion.pos.application.payment;

import com.sion.pos.domain.payment.Payment;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;

public record PaymentCreateCommand(
        Long orderId,
        Payment.Method method,
        Payment.Provider provider
) {
    public PaymentCreateCommand {
        if (orderId == null || orderId <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "주문 정보가 올바르지 않습니다.");
        }

        if (method == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "결제 수단을 선택해주세요.");
        }

        if (method == Payment.Method.EASY_PAY && provider == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "간편결제는 결제사 정보가 필요합니다.");
        }

        if (method != Payment.Method.EASY_PAY && provider != null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "현금/카드 결제는 결제사 정보를 보낼 수 없습니다.");
        }
    }
}