package com.sion.pos.application.order;

import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderItem;
import com.sion.pos.domain.order.OrderItemRepository;
import com.sion.pos.domain.order.OrderRepository;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public List<WaitingOrderInfo> getWaitingOrders(Long storeId) {
        List<Order> orders = orderRepository.findByStoreIdAndOrderDateAndStatusOrderByOrderNumberAsc(
                storeId,
                LocalDate.now(),
                Order.Status.RECEIVED
        );

        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository.findByOrderIdInOrderByIdAsc(orderIds).stream()
                                                                       .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<Long, Payment> paymentByOrderId = paymentRepository.findByOrderIdIn(orderIds).stream()
                                                               .collect(Collectors.toMap(Payment::getOrderId, payment -> payment));

        return orders.stream()
                     .map(order -> toWaitingOrderInfo(order, itemsByOrderId.getOrDefault(order.getId(), List.of()),
                             paymentByOrderId.get(order.getId())))
                     .toList();
    }

    private WaitingOrderInfo toWaitingOrderInfo(Order order, List<OrderItem> items, Payment payment) {
        return new WaitingOrderInfo(
                order.getId(),
                order.getOrderNumber(),
                slotStatus(payment),
                items.stream()
                     .map(item -> new WaitingOrderInfo.Item(item.getMenuName(), item.getQuantity()))
                     .toList(),
                payment != null ? payment.getMethod().name() : null,
                order.getTotalAmount()
        );
    }

    private String slotStatus(Payment payment) {
        if (payment != null && payment.getStatus() == Payment.Status.COMPLETED) {
            return "PAID";
        }

        return "PENDING";
    }
}