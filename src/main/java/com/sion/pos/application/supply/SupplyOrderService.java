package com.sion.pos.application.supply;

import com.sion.pos.domain.supply.SupplyOrder;
import com.sion.pos.domain.supply.SupplyOrderItem;
import com.sion.pos.domain.supply.SupplyOrderItemRepository;
import com.sion.pos.domain.supply.SupplyOrderRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplyOrderService {

    private final SupplyOrderRepository supplyOrderRepository;
    private final SupplyOrderItemRepository supplyOrderItemRepository;

    @Transactional(readOnly = true)
    public List<SupplyOrderInfo> getSupplyOrders(Long storeId) {
        List<SupplyOrder> orders = supplyOrderRepository.findByStoreIdAndDeletedAtIsNullOrderByIdDesc(storeId);
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(SupplyOrder::getId).toList();
        Map<Long, List<SupplyOrderItem>> itemsByOrderId = supplyOrderItemRepository
                .findBySupplyOrderIdInAndDeletedAtIsNullOrderByIdAsc(orderIds).stream()
                .collect(Collectors.groupingBy(SupplyOrderItem::getSupplyOrderId));

        return orders.stream()
                     .map(order -> toInfo(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
                     .toList();
    }

    private SupplyOrderInfo toInfo(SupplyOrder order, List<SupplyOrderItem> items) {
        return new SupplyOrderInfo(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items.stream()
                     .map(item -> new SupplyOrderInfo.Item(
                             item.getItemCode(),
                             item.getItemName(),
                             item.getUnit(),
                             item.getQuantity(),
                             item.getUnitPrice()))
                     .toList());
    }
}