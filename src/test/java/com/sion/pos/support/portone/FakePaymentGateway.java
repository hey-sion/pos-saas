package com.sion.pos.support.portone;

import com.sion.pos.domain.payment.PaymentGateway;
import com.sion.pos.domain.payment.PaymentGatewayResult;
import java.util.HashMap;
import java.util.Map;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class FakePaymentGateway implements PaymentGateway {

    private final Map<String, PaymentGatewayResult> results = new HashMap<>();
    private boolean transactionActiveOnLastLookup;

    public void stub(String pgPaymentId, PaymentGatewayResult result) {
        results.put(pgPaymentId, result);
    }

    public void clear() {
        results.clear();
        transactionActiveOnLastLookup = false;
    }

    /** 외부 호출이 트랜잭션 안에서 일어나면 그동안 DB 커넥션이 묶인다 — 그걸 검증하기 위한 기록. */
    public boolean wasTransactionActiveOnLastLookup() {
        return transactionActiveOnLastLookup;
    }

    @Override
    public PaymentGatewayResult lookup(String pgPaymentId) {
        transactionActiveOnLastLookup = TransactionSynchronizationManager.isActualTransactionActive();

        PaymentGatewayResult result = results.get(pgPaymentId);
        if (result == null) {
            throw new IllegalStateException("Stub not registered for pgPaymentId: " + pgPaymentId);
        }
        return result;
    }
}