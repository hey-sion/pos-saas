package com.sion.pos.domain.payment;

public record PaymentGatewayResult(
        Status status,
        Integer amount,
        String transactionKey,
        String failReason
) {

    public enum Status {
        PAID,
        FAILED,
        PENDING
    }
}