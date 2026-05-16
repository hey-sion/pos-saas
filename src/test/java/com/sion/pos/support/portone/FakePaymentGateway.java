package com.sion.pos.support.portone;

import com.sion.pos.domain.payment.PaymentGateway;
import com.sion.pos.domain.payment.PaymentGatewayResult;
import java.util.HashMap;
import java.util.Map;

public class FakePaymentGateway implements PaymentGateway {

    private final Map<String, PaymentGatewayResult> results = new HashMap<>();

    public void stub(String pgPaymentId, PaymentGatewayResult result) {
        results.put(pgPaymentId, result);
    }

    public void clear() {
        results.clear();
    }

    @Override
    public PaymentGatewayResult lookup(String pgPaymentId) {
        PaymentGatewayResult result = results.get(pgPaymentId);
        if (result == null) {
            throw new IllegalStateException("Stub not registered for pgPaymentId: " + pgPaymentId);
        }
        return result;
    }
}