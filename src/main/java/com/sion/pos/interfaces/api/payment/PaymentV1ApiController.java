package com.sion.pos.interfaces.api.payment;

import com.sion.pos.application.payment.PaymentFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentV1ApiController {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentCreateResponse create(@Valid @RequestBody PaymentCreateRequest request) {
        return PaymentCreateResponse.from(paymentFacade.createPayment(request.toCommand()));
    }

    @PostMapping("/{paymentId}/verify")
    @ResponseStatus(HttpStatus.OK)
    public PaymentVerifyResponse verify(@PathVariable Long paymentId) {
        return PaymentVerifyResponse.from(paymentFacade.verify(paymentId));
    }

    @PostMapping({"/webhook/portone", "/webhook/portone/"})
    @ResponseStatus(HttpStatus.OK)
    public void portOneWebhook(@RequestBody PortOneWebhookRequest request) {
        paymentFacade.handlePortOneWebhook(request.type(), request.pgPaymentId());
    }
}