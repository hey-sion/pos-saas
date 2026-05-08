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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private static final Set<Payment.Method> SUPPORTED_METHODS =
            Set.of(Payment.Method.CASH, Payment.Method.CARD);

    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Order create(OrderCreateCommand command) {
        List<OrderCreateCommand.Line> items = command.items();
        if (items == null || items.isEmpty()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "주문 항목이 비어 있습니다.");
        }

        List<Long> menuIds = items.stream().map(OrderCreateCommand.Line::menuId).toList();
        if (menuIds.stream().distinct().count() != menuIds.size()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "중복된 메뉴가 포함되어 있습니다.");
        }

        if (!SUPPORTED_METHODS.contains(command.method())) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "지원하지 않는 결제 방식입니다.");
        }

        Map<Long, Menu> menuById = menuRepository
                .findByIdInAndStoreIdAndActiveTrueAndDeletedAtIsNull(menuIds, command.storeId())
                .stream()
                .collect(Collectors.toMap(Menu::getId, Function.identity()));
        if (menuById.size() != menuIds.size()) {
            throw new PosApplicationException(ErrorType.NOT_FOUND, "주문할 수 없는 메뉴가 포함되어 있습니다.");
        }

        int totalAmount = items.stream()
                               .mapToInt(line -> menuById.get(line.menuId()).getPrice() * line.quantity())
                               .sum();

        LocalDate orderDate = LocalDate.now();
        int orderNumber = orderRepository
                            .findMaxOrderNumberByStoreIdAndOrderDate(command.storeId(), orderDate)
                            .orElse(0) + 1;

        Order order = orderRepository.save(Order.create(command.storeId(), orderDate, orderNumber, totalAmount));

        List<OrderItem> orderItems = items.stream()
                                          .map(line -> {
                                              Menu menu = menuById.get(line.menuId());
                                              return OrderItem.create(
                                                      order.getId(),
                                                      menu.getId(),
                                                      menu.getName(),
                                                      menu.getPrice(),
                                                      line.quantity());
                                          }).toList();
        orderItemRepository.saveAll(orderItems);

        Payment payment = Payment.create(
                order.getId(),
                command.method(),
                Payment.Channel.OFFLINE,
                totalAmount,
                LocalDateTime.now());
        paymentRepository.save(payment);

        return order;
    }
}
