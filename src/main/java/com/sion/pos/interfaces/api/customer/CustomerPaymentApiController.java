package com.sion.pos.interfaces.api.customer;

import com.sion.pos.application.payment.PaymentFacade;
import com.sion.pos.interfaces.api.payment.PaymentCreateResponse;
import com.sion.pos.interfaces.api.payment.PaymentVerifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 손님 QR 셀프결제용 공개 결제 API.
 */
@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
public class CustomerPaymentApiController {

    private final PaymentFacade paymentFacade;

    @PostMapping("/orders/{orderId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentCreateResponse create(@PathVariable Long orderId) {
        return PaymentCreateResponse.from(paymentFacade.createCustomerPayment(orderId));
    }

    @PostMapping("/payments/{paymentId}/verify")
    @ResponseStatus(HttpStatus.OK)
    public PaymentVerifyResponse verify(@PathVariable Long paymentId) {
        return PaymentVerifyResponse.from(paymentFacade.verifyCustomerPayment(paymentId));
    }
}