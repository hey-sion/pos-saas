package com.sion.pos.application.supply;

import com.sion.pos.domain.supply.SupplyItem;
import com.sion.pos.domain.supply.SupplyOrder;
import com.sion.pos.domain.supply.SupplyOrderItem;
import com.sion.pos.domain.supply.SupplyOrderItemRepository;
import com.sion.pos.domain.supply.SupplyOrderRepository;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplyOrderFacade {

    private final SupplyOrderRepository supplyOrderRepository;
    private final SupplyOrderItemRepository supplyOrderItemRepository;

    @Transactional
    public SupplyOrder createOrder(SupplyOrderCreateCommand command) {
        validateLines(command.items());

        // 가격은 클라이언트가 보낸 값이 아니라 서버 카탈로그(SupplyItem)에서 결정한다.
        int totalAmount = command.items().stream()
                                 .mapToInt(line -> SupplyItem.from(line.itemCode()).getUnitPrice() * line.quantity())
                                 .sum();

        SupplyOrder order = supplyOrderRepository.save(SupplyOrder.create(command.storeId(), totalAmount));

        supplyOrderItemRepository.saveAll(
                command.items().stream()
                       .map(line -> toItem(order.getId(), line))
                       .toList());

        return order;
    }

    private void validateLines(List<SupplyOrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "발주 항목이 비어 있습니다.");
        }

        List<String> codes = lines.stream().map(SupplyOrderLine::itemCode).toList();
        if (codes.stream().distinct().count() != codes.size()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "중복된 품목이 포함되어 있습니다.");
        }
    }

    private SupplyOrderItem toItem(Long supplyOrderId, SupplyOrderLine line) {
        SupplyItem item = SupplyItem.from(line.itemCode());
        return SupplyOrderItem.create(
                supplyOrderId,
                item.name(),
                item.getItemName(),
                item.getUnit(),
                item.getUnitPrice(),
                line.quantity());
    }
}