package com.sion.pos.interfaces.api.payment;

public record PortOneWebhookRequest(
        String type,
        WebhookData data
) {

    public record WebhookData(String paymentId) {
    }

    public String pgPaymentId() {
        return data == null ? null : data.paymentId();
    }
}