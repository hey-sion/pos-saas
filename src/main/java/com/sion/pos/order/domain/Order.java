package com.sion.pos.order.domain;

import com.sion.pos.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "order_number", nullable = false)
    private Integer orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    public enum Status {
        PENDING,
        PAID,
        DELIVERED,
        CANCELLED
    }
}