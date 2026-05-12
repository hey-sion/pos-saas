package com.sion.pos.domain.payment;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;

public record PaymentGatewayResult(
        Status status,
        Integer amount,
        String transactionKey,
        String failReason
) {

    public PaymentGatewayResult {
        if (status == null) {
            throw new PosApplicationException(ErrorType.INTERNAL_ERROR, "status는 필수입니다.");
        }
    }

    public enum Status {
        PAID,
        FAILED,
        PENDING
    }
}