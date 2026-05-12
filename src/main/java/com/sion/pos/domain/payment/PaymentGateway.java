package com.sion.pos.domain.payment;

public interface PaymentGateway {

    PaymentGatewayResult lookup(String pgPaymentId);
}