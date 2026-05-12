package com.sion.pos.application.order;

import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderItem;
import com.sion.pos.domain.order.OrderItemRepository;
import com.sion.pos.domain.order.OrderRepository;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentRepository;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Transactional
    public void updateStatus(Long orderId, Order.Status status) {
        Order order = orderRepository.findById(orderId)
                                     .orElseThrow(() -> new PosApplicationException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        switch (status) {
            case DELIVERED -> order.deliver();
            case CANCELLED -> order.cancel();
            case RECEIVED -> throw new PosApplicationException(ErrorType.BAD_REQUEST, "변경할 수 없는 주문 상태입니다.");
        }
    }

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
        Map<Long, Payment> paymentByOrderId = activePaymentByOrderId(orderIds);

        return orders.stream()
                     .map(order -> toWaitingOrderInfo(order, itemsByOrderId.getOrDefault(order.getId(), List.of()),
                                    paymentByOrderId.get(order.getId())))
                     .toList();
    }

    @Transactional(readOnly = true)
    public DailyOrderSummaryInfo getDailySummary(Long storeId, LocalDate date) {
        List<Order> orders = orderRepository.findByStoreIdAndOrderDateOrderByOrderNumberAsc(storeId, date);
        if (orders.isEmpty()) {
            return new DailyOrderSummaryInfo(date, 0, 0, 0, List.of());
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository.findByOrderIdInOrderByIdAsc(orderIds).stream()
                                                                       .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<Long, Payment> paymentByOrderId = activePaymentByOrderId(orderIds);

        int salesAmount = orders.stream()
                                .filter(order -> order.getStatus() == Order.Status.DELIVERED)
                                .mapToInt(Order::getTotalAmount)
                                .sum();
        int salesOrderCount = (int) orders.stream()
                                          .filter(order -> order.getStatus() == Order.Status.DELIVERED)
                                          .count();

        List<DailyOrderSummaryInfo.OrderInfo> orderInfos = orders.stream()
                                                                 .map(order -> toDailyOrderInfo(
                                                                         order,
                                                                         itemsByOrderId.getOrDefault(order.getId(), List.of()),
                                                                         paymentByOrderId.get(order.getId())))
                                                                 .toList();

        return new DailyOrderSummaryInfo(date, salesAmount, salesOrderCount, orders.size(), orderInfos);
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

    private Map<Long, Payment> activePaymentByOrderId(List<Long> orderIds) {
        return paymentRepository.findByOrderIdIn(orderIds).stream()
                                .collect(Collectors.groupingBy(Payment::getOrderId))
                                .entrySet().stream()
                                .map(entry -> Map.entry(entry.getKey(), pickActivePayment(entry.getValue())))
                                .filter(entry -> entry.getValue().isPresent())
                                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
    }

    private Optional<Payment> pickActivePayment(List<Payment> payments) {
        Optional<Payment> latestCompleted = payments.stream()
                                                    .filter(payment -> payment.getStatus() == Payment.Status.COMPLETED)
                                                    .max(Comparator.comparing(Payment::getId));
        if (latestCompleted.isPresent()) {
            return latestCompleted;
        }

        return payments.stream()
                       .filter(payment -> payment.getStatus() == Payment.Status.PENDING)
                       .max(Comparator.comparing(Payment::getId));
    }

    private String slotStatus(Payment payment) {
        if (payment != null && payment.getStatus() == Payment.Status.COMPLETED) {
            return "PAID";
        }

        return "PENDING";
    }

    private DailyOrderSummaryInfo.OrderInfo toDailyOrderInfo(Order order, List<OrderItem> items, Payment payment) {
        return new DailyOrderSummaryInfo.OrderInfo(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                items.stream()
                     .map(item -> new DailyOrderSummaryInfo.Item(item.getMenuName(), item.getQuantity()))
                     .toList(),
                payment != null ? payment.getMethod().name() : null,
                order.getTotalAmount()
        );
    }
}