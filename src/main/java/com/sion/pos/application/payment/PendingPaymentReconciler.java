package com.sion.pos.application.payment;

import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentGateway;
import com.sion.pos.domain.payment.PaymentGatewayResult;
import com.sion.pos.domain.payment.PaymentRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PG 장애로 verify·웹훅이 모두 실패해 확정되지 못한 결제를 주기적으로 재조회한다.
 * verify·웹훅과 같은 {@link PaymentResultApplier}를 거치므로 반영 지점은 한 곳이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingPaymentReconciler {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentResultApplier paymentResultApplier;
    private final PaymentReconcileProperties properties;

    @Scheduled(fixedDelayString = "${payment.reconcile.delay}")
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        List<Payment> targets = paymentRepository.findPendingPgPayments(
                now.minus(properties.grace()),
                now.minus(properties.lookback()),
                properties.batchSize());
        if (targets.isEmpty()) {
            return;
        }

        int failed = 0;
        for (Payment payment : targets) {
            if (!reconcileOne(payment)) {
                failed++;
            }
        }
        log.info("[PAYMENT_RECONCILE] total={} failed={}", targets.size(), failed);
    }

    /** 한 건이 실패해도 나머지를 계속 처리해야 하므로 예외를 여기서 삼킨다. */
    private boolean reconcileOne(Payment payment) {
        try {
            PaymentGatewayResult result = paymentGateway.lookup(payment.getPgPaymentId());
            paymentResultApplier.apply(payment.getId(), result);
            return true;
        } catch (Exception e) {
            log.error("[PAYMENT_RECONCILE] lookup failed. paymentId={} pgPaymentId={}",
                    payment.getId(), payment.getPgPaymentId(), e);
            return false;
        }
    }
}