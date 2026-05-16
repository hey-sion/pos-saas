package com.sion.pos.application.order;

import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderItem;
import com.sion.pos.domain.order.OrderItemRepository;
import com.sion.pos.domain.order.OrderRepository;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentRepository;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.LocalDate;
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

    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderNumberIssuer orderNumberIssuer;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Order createOrder(OrderCreateCommand command) {
        validateLines(command.items());

        Map<Long, Menu> menuById = menuRepository
                .findByIdInAndStoreIdAndActiveTrueAndDeletedAtIsNull(menuIds(command.items()), command.storeId())
                .stream()
                .collect(Collectors.toMap(Menu::getId, Function.identity()));
        if (menuById.size() != command.items().size()) {
            throw new PosApplicationException(ErrorType.NOT_FOUND, "주문할 수 없는 메뉴가 포함되어 있습니다.");
        }

        int totalAmount = calculateTotalAmount(command.items(), menuById);

        LocalDate orderDate = LocalDate.now();
        int orderNumber = orderNumberIssuer.issue(command.storeId(), orderDate);

        Order order = orderRepository.save(Order.create(command.storeId(), orderDate, orderNumber, totalAmount));

        orderItemRepository.saveAll(toOrderItems(order.getId(), command.items(), menuById));

        return order;
    }

    @Transactional
    public Order updateOrderItems(Long orderId, OrderUpdateItemsCommand command) {
        if (orderId == null || orderId <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "orderId는 1 이상이어야 합니다.");
        }
        validateLines(command.items());

        Order order = orderRepository.findById(orderId)
                                     .orElseThrow(() -> new PosApplicationException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (order.getStatus() != Order.Status.RECEIVED) {
            throw new PosApplicationException(ErrorType.CONFLICT,
                    "주문 접수 상태에서만 수정 가능합니다. 현재 상태: " + order.getStatus());
        }

        List<Payment> payments = paymentRepository.findByOrderIdIn(List.of(order.getId()));
        boolean alreadyCompleted = payments.stream()
                                           .anyMatch(payment -> payment.getStatus() == Payment.Status.COMPLETED);
        if (alreadyCompleted) {
            throw new PosApplicationException(ErrorType.CONFLICT, "이미 결제 완료된 주문은 수정할 수 없습니다.");
        }

        Map<Long, Menu> menuById = menuRepository
                .findByIdInAndStoreIdAndActiveTrueAndDeletedAtIsNull(menuIds(command.items()), order.getStoreId())
                .stream()
                .collect(Collectors.toMap(Menu::getId, Function.identity()));
        if (menuById.size() != command.items().size()) {
            throw new PosApplicationException(ErrorType.NOT_FOUND, "주문할 수 없는 메뉴가 포함되어 있습니다.");
        }

        invalidatePendingPgPayments(payments);
        order.changeTotalAmount(calculateTotalAmount(command.items(), menuById));

        List<OrderItem> existingItems = orderItemRepository.findByOrderIdInAndDeletedAtIsNullOrderByIdAsc(List.of(order.getId()));
        existingItems.forEach(OrderItem::delete);
        orderItemRepository.saveAll(toOrderItems(order.getId(), command.items(), menuById));

        return order;
    }

    private void validateLines(List<OrderItemLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "주문 항목이 비어 있습니다.");
        }

        List<Long> menuIds = menuIds(lines);
        if (menuIds.stream().distinct().count() != menuIds.size()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "중복된 메뉴가 포함되어 있습니다.");
        }
    }

    private List<Long> menuIds(List<OrderItemLine> lines) {
        return lines.stream().map(OrderItemLine::menuId).toList();
    }

    private int calculateTotalAmount(List<OrderItemLine> lines, Map<Long, Menu> menuById) {
        return lines.stream()
                    .mapToInt(line -> menuById.get(line.menuId()).getPrice() * line.quantity())
                    .sum();
    }

    private List<OrderItem> toOrderItems(Long orderId, List<OrderItemLine> lines, Map<Long, Menu> menuById) {
        return lines.stream()
                    .map(line -> {
                        Menu menu = menuById.get(line.menuId());
                        return OrderItem.create(
                                orderId,
                                menu.getId(),
                                menu.getName(),
                                menu.getPrice(),
                                line.quantity());
                    }).toList();
    }

    private void invalidatePendingPgPayments(List<Payment> payments) {
        payments.stream()
                .filter(payment -> payment.getChannel() == Payment.Channel.PG)
                .filter(payment -> payment.getStatus() == Payment.Status.PENDING)
                .forEach(payment -> payment.fail("주문 수정으로 기존 결제 요청 무효화"));
    }
}
