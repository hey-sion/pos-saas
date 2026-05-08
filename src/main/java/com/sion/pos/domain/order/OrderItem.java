package com.sion.pos.domain.order;

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
@Table(name = "order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "menu_name", nullable = false, length = 100)
    private String menuName;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer quantity;

    public static OrderItem create(Long orderId, Long menuId, String menuName, Integer price, Integer quantity) {
        if (orderId == null || orderId <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "orderId는 1 이상이어야 합니다.");
        }
        if (menuId == null || menuId <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "menuId는 1 이상이어야 합니다.");
        }
        if (menuName == null || menuName.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "menuName은 필수입니다.");
        }
        if (price == null || price <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "price는 1 이상이어야 합니다.");
        }
        if (quantity == null || quantity <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "quantity는 1 이상이어야 합니다.");
        }

        OrderItem item = new OrderItem();
        item.orderId = orderId;
        item.menuId = menuId;
        item.menuName = menuName;
        item.price = price;
        item.quantity = quantity;
        return item;
    }
}
