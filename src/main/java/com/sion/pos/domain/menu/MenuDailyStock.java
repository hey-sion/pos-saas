package com.sion.pos.domain.menu;

import com.sion.pos.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "menu_daily_stock",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_menu_daily_stock_menu_date",
                columnNames = {"menu_id", "stock_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuDailyStock extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "stock_date", nullable = false)
    private LocalDate stockDate;

    @Column(name = "limit_quantity", nullable = false)
    private Integer limitQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private Integer soldQuantity;
}