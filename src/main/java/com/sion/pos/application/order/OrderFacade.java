package com.sion.pos.application.order;

import com.sion.pos.application.menu.MenuStockService;
import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderItem;
import com.sion.pos.domain.order.OrderItemRepository;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentRepository;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final MenuRepository menuRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderService orderService;
    private final OrderNumberIssuer orderNumberIssuer;
    private final OrderCreator orderCreator;
    private final PaymentRepository paymentRepository;
    private final MenuStockService menuStockService;

    public Order createOrder(OrderCreateCommand command) {
        OrderItemLines.validate(command.items());

        LocalDate orderDate = LocalDate.now(BUSINESS_ZONE);
        int orderNumber = orderNumberIssuer.issue(command.storeId(), orderDate);

        return orderCreator.create(orderNumber, orderDate, command);
    }

    @Transactional
    public Order updateOrderItems(Long storeId, Long orderId, OrderUpdateItemsCommand command) {
        if (storeId == null || storeId <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "storeId는 1 이상이어야 합니다.");
        }

        if (orderId == null || orderId <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "orderId는 1 이상이어야 합니다.");
        }

        OrderItemLines.validate(command.items());

        Order order = orderService.getOrder(storeId, orderId);
        if (order.getStatus() != Order.Status.PAYMENT_PENDING) {
            throw new PosApplicationException(ErrorType.CONFLICT,
                    "결제 대기 상태에서만 수정 가능합니다. 현재 상태: " + order.getStatus());
        }

        List<Payment> payments = paymentRepository.findByOrderIdIn(List.of(order.getId()));
        boolean alreadyCompleted = payments.stream()
                                           .anyMatch(payment -> payment.getStatus() == Payment.Status.COMPLETED);
        if (alreadyCompleted) {
            throw new PosApplicationException(ErrorType.CONFLICT, "이미 결제 완료된 주문은 수정할 수 없습니다.");
        }

        Map<Long, Menu> menuById = menuRepository
                .findByIdInAndStoreIdAndActiveTrueAndDeletedAtIsNull(OrderItemLines.menuIds(command.items()), storeId)
                .stream()
                .collect(Collectors.toMap(Menu::getId, Function.identity()));
        if (menuById.size() != command.items().size()) {
            throw new PosApplicationException(ErrorType.NOT_FOUND, "주문할 수 없는 메뉴가 포함되어 있습니다.");
        }

        invalidatePendingPgPayments(payments);
        order.changeTotalAmount(OrderItemLines.totalAmount(command.items(), menuById));

        List<OrderItem> existingItems = orderItemRepository.findByOrderIdInAndDeletedAtIsNullOrderByIdAsc(List.of(order.getId()));
        menuStockService.restore(storeId, order.getOrderDate(), OrderItemLines.toRestoreLines(existingItems));
        menuStockService.deduct(storeId, order.getOrderDate(), OrderItemLines.toDeductLines(command.items(), menuById));

        existingItems.forEach(OrderItem::delete);
        orderItemRepository.saveAll(OrderItemLines.toOrderItems(order.getId(), command.items(), menuById));

        return order;
    }

    private void invalidatePendingPgPayments(List<Payment> payments) {
        payments.stream()
                .filter(payment -> payment.getChannel() == Payment.Channel.PG)
                .filter(payment -> payment.getStatus() == Payment.Status.PENDING)
                .forEach(payment -> payment.fail("주문 수정으로 기존 결제 요청 무효화"));
    }
}