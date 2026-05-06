package com.sion.pos.order.domain;

import com.sion.pos.global.domain.BaseEntity;
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
}