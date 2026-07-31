package com.sion.pos.application.order;

import com.sion.pos.application.menu.MenuStockService;
import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderItemRepository;
import com.sion.pos.domain.order.OrderRepository;
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
public class OrderCreator {

    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuStockService menuStockService;

    @Transactional
    public Order create(int orderNumber, LocalDate orderDate, OrderCreateCommand command) {
        Map<Long, Menu> menuById = loadOrderableMenus(command.storeId(), command.items());
        int totalAmount = OrderItemLines.totalAmount(command.items(), menuById);

        menuStockService.deduct(command.storeId(), orderDate, OrderItemLines.toDeductLines(command.items(), menuById));

        Order order = orderRepository.save(
                Order.create(command.storeId(), orderDate, orderNumber, totalAmount));
        orderItemRepository.saveAll(OrderItemLines.toOrderItems(order.getId(), command.items(), menuById));

        return order;
    }

    private Map<Long, Menu> loadOrderableMenus(Long storeId, List<OrderItemLine> lines) {
        Map<Long, Menu> menuById = menuRepository
                .findByIdInAndStoreIdAndActiveTrueAndDeletedAtIsNull(OrderItemLines.menuIds(lines), storeId)
                .stream()
                .collect(Collectors.toMap(Menu::getId, Function.identity()));

        if (menuById.size() != lines.size()) {
            throw new PosApplicationException(ErrorType.NOT_FOUND, "주문할 수 없는 메뉴가 포함되어 있습니다.");
        }

        return menuById;
    }
}