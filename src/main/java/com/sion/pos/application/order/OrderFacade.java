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
import com.sion.pos.support.portone.PortOneProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private static final Set<Payment.Method> OFFLINE_METHODS =
            Set.of(Payment.Method.CASH, Payment.Method.CARD);

    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final OrderNumberIssuer orderNumberIssuer;
    private final PortOneProperties portOneProperties;

    @Transactional
    public Order createOffline(OfflineOrderCreateCommand command) {
        if (command.method() == null || !OFFLINE_METHODS.contains(command.method())) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "오프라인 결제는 CASH 또는 CARD 만 지원합니다.");
        }

        List<CommonLine> lines = command.items() == null ? List.of()
                : command.items().stream()
                         .map(line -> new CommonLine(line.menuId(), line.quantity()))
                         .toList();
        OrderContext ctx = buildOrder(command.storeId(), lines);

        Payment payment = Payment.createOffline(
                ctx.order.getId(),
                command.method(),
                ctx.order.getTotalAmount(),
                LocalDateTime.now());
        paymentRepository.save(payment);

        return ctx.order;
    }

    @Transactional
    public EasyPayOrderCreateInfo createEasyPay(EasyPayOrderCreateCommand command) {
        if (command.provider() == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "간편결제는 provider가 필수입니다.");
        }

        List<CommonLine> lines = command.items() == null ? List.of()
                : command.items().stream()
                         .map(line -> new CommonLine(line.menuId(), line.quantity()))
                         .toList();
        OrderContext ctx = buildOrder(command.storeId(), lines);

        String pgPaymentId = generatePgPaymentId();
        Payment payment = paymentRepository.save(Payment.createPg(
                ctx.order.getId(),
                command.provider(),
                ctx.order.getTotalAmount(),
                pgPaymentId));

        return new EasyPayOrderCreateInfo(
                ctx.order.getId(),
                ctx.order.getOrderNumber(),
                ctx.order.getTotalAmount(),
                payment.getId(),
                payment.getPgPaymentId(),
                payment.getProvider(),
                buildOrderName(ctx.items),
                portOneProperties.storeId(),
                portOneProperties.channelKeyOf(command.provider()));
    }

    private OrderContext buildOrder(Long storeId, List<CommonLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "주문 항목이 비어 있습니다.");
        }

        List<Long> menuIds = lines.stream().map(CommonLine::menuId).toList();
        if (menuIds.stream().distinct().count() != menuIds.size()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "중복된 메뉴가 포함되어 있습니다.");
        }

        Map<Long, Menu> menuById = menuRepository
                .findByIdInAndStoreIdAndActiveTrueAndDeletedAtIsNull(menuIds, storeId)
                .stream()
                .collect(Collectors.toMap(Menu::getId, Function.identity()));
        if (menuById.size() != menuIds.size()) {
            throw new PosApplicationException(ErrorType.NOT_FOUND, "주문할 수 없는 메뉴가 포함되어 있습니다.");
        }

        int totalAmount = lines.stream()
                               .mapToInt(line -> menuById.get(line.menuId()).getPrice() * line.quantity())
                               .sum();

        LocalDate orderDate = LocalDate.now();
        int orderNumber = orderNumberIssuer.issue(storeId, orderDate);

        Order order = orderRepository.save(Order.create(storeId, orderDate, orderNumber, totalAmount));

        List<OrderItem> orderItems = lines.stream()
                                          .map(line -> {
                                              Menu menu = menuById.get(line.menuId());
                                              return OrderItem.create(
                                                      order.getId(),
                                                      menu.getId(),
                                                      menu.getName(),
                                                      menu.getPrice(),
                                                      line.quantity());
                                          }).toList();
        List<OrderItem> savedItems = orderItemRepository.saveAll(orderItems);

        return new OrderContext(order, savedItems);
    }

    private String generatePgPaymentId() {
        return "easypay-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String buildOrderName(List<OrderItem> items) {
        if (items.isEmpty()) {
            return "주문";
        }
        String first = items.get(0).getMenuName();
        if (items.size() == 1) {
            return first;
        }
        return first + " 외 " + (items.size() - 1) + "건";
    }

    private record OrderContext(Order order, List<OrderItem> items) {}

    private record CommonLine(Long menuId, Integer quantity) {}
}