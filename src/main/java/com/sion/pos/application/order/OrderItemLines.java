package com.sion.pos.application.order;

import com.sion.pos.application.menu.MenuStockDeductLine;
import com.sion.pos.application.menu.MenuStockRestoreLine;
import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.order.OrderItem;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.util.List;
import java.util.Map;

final class OrderItemLines {

    private OrderItemLines() {
    }

    static void validate(List<OrderItemLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "주문 항목이 비어 있습니다.");
        }

        List<Long> menuIds = menuIds(lines);
        if (menuIds.stream().distinct().count() != menuIds.size()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "중복된 메뉴가 포함되어 있습니다.");
        }
    }

    static List<Long> menuIds(List<OrderItemLine> lines) {
        return lines.stream().map(OrderItemLine::menuId).toList();
    }

    static int totalAmount(List<OrderItemLine> lines, Map<Long, Menu> menuById) {
        return lines.stream()
                    .mapToInt(line -> menuById.get(line.menuId()).getPrice() * line.quantity())
                    .sum();
    }

    static List<MenuStockDeductLine> toDeductLines(List<OrderItemLine> lines, Map<Long, Menu> menuById) {
        return lines.stream()
                    .filter(line -> menuById.get(line.menuId()).hasDailyLimit())
                    .map(line -> {
                        Menu menu = menuById.get(line.menuId());
                        return new MenuStockDeductLine(
                                menu.getId(),
                                menu.getName(),
                                menu.getDailyLimitQuantity(),
                                line.quantity());
                    }).toList();
    }

    static List<MenuStockRestoreLine> toRestoreLines(List<OrderItem> items) {
        return items.stream()
                    .map(item -> new MenuStockRestoreLine(item.getMenuId(), item.getQuantity()))
                    .toList();
    }

    static List<OrderItem> toOrderItems(Long orderId, List<OrderItemLine> lines, Map<Long, Menu> menuById) {
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
}