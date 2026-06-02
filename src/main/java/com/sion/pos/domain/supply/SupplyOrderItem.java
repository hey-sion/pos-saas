package com.sion.pos.domain.supply;

import com.sion.pos.domain.BaseEntity;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "supply_order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplyOrderItem extends BaseEntity {

    @Column(name = "supply_order_id", nullable = false)
    private Long supplyOrderId;

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice;

    public static SupplyOrderItem create(
            Long supplyOrderId,
            String itemCode,
            String itemName,
            String unit,
            Integer unitPrice,
            Integer quantity
    ) {
        if (supplyOrderId == null || supplyOrderId <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "supplyOrderId는 1 이상이어야 합니다.");
        }
        if (itemCode == null || itemCode.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "itemCode는 필수입니다.");
        }
        if (itemName == null || itemName.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "itemName은 필수입니다.");
        }
        if (unit == null || unit.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "unit은 필수입니다.");
        }
        if (unitPrice == null || unitPrice <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "unitPrice는 1 이상이어야 합니다.");
        }
        if (quantity == null || quantity <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "quantity는 1 이상이어야 합니다.");
        }

        SupplyOrderItem item = new SupplyOrderItem();
        item.supplyOrderId = supplyOrderId;
        item.itemCode = itemCode;
        item.itemName = itemName;
        item.unit = unit;
        item.unitPrice = unitPrice;
        item.quantity = quantity;
        return item;
    }
}