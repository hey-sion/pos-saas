package com.sion.pos.application.payment;

import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderRepository;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentGatewayResult;
import com.sion.pos.domain.payment.PaymentRepository;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultApplier {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public void apply(Long paymentId, PaymentGatewayResult result) {
        Payment payment = paymentRepository.findById(paymentId)
                                           .orElseThrow(() -> new PosApplicationException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."));

        switch (result.status()) {
            case PAID -> {
                if (!payment.matchesAmount(result.amount())) {
                    log.error("[AMOUNT_MISMATCH] paymentId={} expected={} actual={}", payment.getId(), payment.getAmount(), result.amount());
                    paymentRepository.markMismatchIfPending(payment.getId());
                    return;
                }

                int completed = paymentRepository.completeIfPending(
                        payment.getId(),
                        LocalDateTime.now(BUSINESS_ZONE),
                        result.transactionKey());
                if (completed == 1) {
                    Order order = orderRepository.findById(payment.getOrderId())
                                                 .orElseThrow(() -> new PosApplicationException(
                                                         ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
                    order.markReceived();
                }
            }
            case FAILED -> paymentRepository.failIfPending(payment.getId(), result.failReason());
            case PENDING -> {
                // PortOne 측에서도 아직 결제 완료 안 됨. PENDING 그대로 유지.
            }
        }
    }
}