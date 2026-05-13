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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private static final String CURRENCY_KRW = "KRW";
    private static final String PAY_METHOD_EASY_PAY = "EASY_PAY";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PortOneProperties portOneProperties;

    @Transactional
    public PaymentCreateInfo createPayment(PaymentCreateCommand command) {
        validateCommand(command);

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
            return new PaymentCreateInfo(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getMethod(),
                    payment.getStatus(),
                    payment.getAmount(),
                    new PaymentCreateInfo.PgRequestParams(
                            payment.getPgPaymentId(),
                            portOneProperties.storeId(),
                            portOneProperties.channelKeyOf(command.provider()),
                            buildOrderName(order.getId()),
                            order.getTotalAmount(),
                            PAY_METHOD_EASY_PAY,
                            new PaymentCreateInfo.PgRequestParams.EasyPay(command.provider().toPortOneCode()),
                            CURRENCY_KRW
                    )
            );
        }

        Payment payment = paymentRepository.save(Payment.createOffline(
                order.getId(),
                command.method(),
                order.getTotalAmount(),
                LocalDateTime.now()));
        return new PaymentCreateInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmount(),
                null
        );
    }

    @Transactional
    public PaymentVerifyInfo verify(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                                           .orElseThrow(() -> new PosApplicationException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."));

        if (payment.getChannel() != Payment.Channel.PG) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "PG 결제만 검증할 수 있습니다.");
        }

        if (payment.getStatus() != Payment.Status.PENDING) {
            return PaymentVerifyInfo.from(payment);
        }

        PaymentGatewayResult result = paymentGateway.lookup(payment.getPgPaymentId());

        switch (result.status()) {
            case PAID -> {
                if (!payment.getAmount().equals(result.amount())) {
                    throw new PosApplicationException(ErrorType.CONFLICT,
                            "결제 금액이 일치하지 않습니다. 요청 금액: " + payment.getAmount() + ", PG 응답 금액: " + result.amount());
                }

                payment.complete(LocalDateTime.now(), result.transactionKey());
            }
            case FAILED -> payment.fail(result.failReason());
            case PENDING -> {
                // PortOne 측에서도 아직 결제 완료 안 됨. PENDING 그대로 반환.
            }
        }

        return PaymentVerifyInfo.from(payment);
    }

    private void validateCommand(PaymentCreateCommand command) {
        if (command.orderId() == null || command.orderId() <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "주문 정보가 올바르지 않습니다.");
        }

        if (command.method() == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "결제 수단을 선택해주세요.");
        }

        if (command.method() == Payment.Method.EASY_PAY && command.provider() == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "간편결제는 결제사 정보가 필요합니다.");
        }

        if (command.method() != Payment.Method.EASY_PAY && command.provider() != null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "현금/카드 결제는 결제사 정보를 보낼 수 없습니다.");
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
