package com.sion.pos.interfaces.api.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sion.pos.application.payment.PaymentFacade;
import com.sion.pos.support.security.LoginStore;
import com.sion.pos.support.portone.PortOneWebhookVerifier;
import com.sion.pos.support.portone.WebhookVerificationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentV1ApiController {

    private final PaymentFacade paymentFacade;
    private final PortOneWebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentCreateResponse create(@LoginStore Long storeId, @Valid @RequestBody PaymentCreateRequest request) {
        return PaymentCreateResponse.from(paymentFacade.createPayment(storeId, request.toCommand()));
    }

    @PostMapping("/{paymentId}/verify")
    @ResponseStatus(HttpStatus.OK)
    public PaymentVerifyResponse verify(@LoginStore Long storeId, @PathVariable Long paymentId) {
        return PaymentVerifyResponse.from(paymentFacade.verify(storeId, paymentId));
    }

    @PostMapping({"/webhook/portone", "/webhook/portone/"})
    public ResponseEntity<Void> portOneWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-signature", required = false) String webhookSignature,
            @RequestHeader(value = "webhook-timestamp", required = false) String webhookTimestamp) {
        try {
            webhookVerifier.verify(rawBody, webhookId, webhookSignature, webhookTimestamp);
        } catch (WebhookVerificationException e) {
            log.warn("[PORTONE_WEBHOOK] 서명 검증 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PortOneWebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, PortOneWebhookRequest.class);
        } catch (JsonProcessingException e) {
            log.warn("[PORTONE_WEBHOOK] 본문 파싱 실패: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        paymentFacade.handlePortOneWebhook(request.type(), request.pgPaymentId());
        return ResponseEntity.ok().build();
    }
}
