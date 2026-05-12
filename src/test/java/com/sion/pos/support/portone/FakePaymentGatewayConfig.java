package com.sion.pos.support.portone;

import com.sion.pos.domain.payment.PaymentGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FakePaymentGatewayConfig {

    @Bean
    @Primary
    public PaymentGateway paymentGateway() {
        return new FakePaymentGateway();
    }
}