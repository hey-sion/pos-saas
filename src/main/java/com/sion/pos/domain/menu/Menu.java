package com.sion.pos.domain.menu;

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
@Table(name = "menu")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "daily_limit_quantity")
    private Integer dailyLimitQuantity;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public static Menu create(Long storeId, String name, Integer price, Integer sortOrder) {
        if (storeId == null || storeId <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "storeId는 1 이상이어야 합니다.");
        }
        if (name == null || name.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "name은 필수입니다.");
        }
        if (price == null || price <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "price는 1 이상이어야 합니다.");
        }

        Menu menu = new Menu();
        menu.storeId = storeId;
        menu.name = name;
        menu.price = price;
        menu.sortOrder = sortOrder != null ? sortOrder : 0;
        menu.active = true;
        return menu;
    }

    public boolean hasDailyLimit() {
        return dailyLimitQuantity != null;
    }

    public void changeDailyLimitQuantity(Integer dailyLimitQuantity) {
        if (dailyLimitQuantity != null && dailyLimitQuantity <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "dailyLimitQuantity는 1 이상이어야 합니다.");
        }

        this.dailyLimitQuantity = dailyLimitQuantity;
    }
}
