package com.sion.pos.application.payment;

import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderItem;
import com.sion.pos.domain.order.OrderItemRepository;
import com.sion.pos.domain.order.OrderRepository;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentGateway;
import com.sion.pos.domain.payment.PaymentGatewayResult;
import com.sion.pos.domain.payment.PaymentRepository;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import com.sion.pos.support.portone.PortOneProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private static final Set<String> HANDLED_TYPES = Set.of("Transaction.Paid", "Transaction.Failed");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PortOneProperties portOneProperties;

    @Transactional
    public PaymentCreateInfo createPayment(PaymentCreateCommand command) {
        Order order = orderRepository.findById(command.orderId())
                                     .orElseThrow(() -> new PosApplicationException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (order.getStatus() != Order.Status.RECEIVED) {
            throw new PosApplicationException(ErrorType.CONFLICT,
                    "결제할 수 없는 주문 상태입니다. 현재 상태: " + order.getStatus());
        }

        boolean alreadyCompleted = paymentRepository.findByOrderIdIn(List.of(order.getId())).stream()
                                                    .anyMatch(payment -> payment.getStatus() == Payment.Status.COMPLETED);
        if (alreadyCompleted) {
            throw new PosApplicationException(ErrorType.CONFLICT, "이미 결제 완료된 주문입니다.");
        }

        if (command.method() == Payment.Method.EASY_PAY) {
            Payment payment = paymentRepository.save(Payment.createPg(
                    order.getId(),
                    command.provider(),
                    order.getTotalAmount(),
                    generatePgPaymentId()));
            return PaymentCreateInfo.pg(
                    payment,
                    portOneProperties.storeId(),
                    portOneProperties.channelKeyOf(command.provider()),
                    buildOrderName(order.getId())
            );
        }

        Payment payment = paymentRepository.save(Payment.createOffline(
                order.getId(),
                command.method(),
                order.getTotalAmount(),
                LocalDateTime.now()));
        return PaymentCreateInfo.offline(payment);
    }

    @Transactional
    public PaymentVerifyInfo verify(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                                           .orElseThrow(() -> new PosApplicationException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."));

        if (!payment.isPgChannel()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "PG 결제만 검증할 수 있습니다.");
        }

        if (!payment.isPending()) {
            return PaymentVerifyInfo.from(payment);
        }

        applyGatewayResult(payment);

        Payment refreshed = paymentRepository.findById(payment.getId())
                                             .orElseThrow(() -> new PosApplicationException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."));
        return PaymentVerifyInfo.from(refreshed);
    }

    @Transactional
    public void handlePortOneWebhook(String type, String pgPaymentId) {
        if (!HANDLED_TYPES.contains(type)) {
            log.info("[PORTONE_WEBHOOK] ignored type={} pgPaymentId={}", type, pgPaymentId);
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findByPgPaymentId(pgPaymentId);
        if (paymentOpt.isEmpty()) {
            log.warn("[PORTONE_WEBHOOK] payment not found. pgPaymentId={}", pgPaymentId);
            return;
        }
        Payment payment = paymentOpt.get();

        if (!payment.isPgChannel()) {
            log.warn("[PORTONE_WEBHOOK] non-PG payment. paymentId={} channel={}", payment.getId(), payment.getChannel());
            return;
        }

        if (!payment.isPending()) {
            log.info("[PORTONE_WEBHOOK] already processed. paymentId={} status={}", payment.getId(), payment.getStatus());
            return;
        }

        applyGatewayResult(payment);
    }

    private void applyGatewayResult(Payment payment) {
        PaymentGatewayResult result = paymentGateway.lookup(payment.getPgPaymentId());

        switch (result.status()) {
            case PAID -> {
                if (!payment.matchesAmount(result.amount())) {
                    log.error("[AMOUNT_MISMATCH] paymentId={} expected={} actual={}", payment.getId(), payment.getAmount(), result.amount());
                    return;
                }

                paymentRepository.completeIfPending(payment.getId(), LocalDateTime.now(), result.transactionKey());
            }
            case FAILED -> paymentRepository.failIfPending(payment.getId(), result.failReason());
            case PENDING -> {
                // PortOne 측에서도 아직 결제 완료 안 됨. PENDING 그대로 유지.
            }
        }
    }

    private String generatePgPaymentId() {
        return "easypay-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String buildOrderName(Long orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderIdInAndDeletedAtIsNullOrderByIdAsc(List.of(orderId));
        if (items.isEmpty()) {
            return "주문";
        }

        String first = items.get(0).getMenuName();
        if (items.size() == 1) {
            return first;
        }

        return first + " 외 " + (items.size() - 1) + "건";
    }
}