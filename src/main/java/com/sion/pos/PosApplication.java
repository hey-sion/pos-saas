package com.sion.pos;

import com.sion.pos.application.payment.PaymentReconcileProperties;
import com.sion.pos.support.portone.PortOneProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({PortOneProperties.class, PaymentReconcileProperties.class})
public class PosApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosApplication.class, args);
    }
}
