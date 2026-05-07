package com.sion.pos.domain.order;

import com.sion.pos.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orders_store_date_number",
                columnNames = {"store_id", "order_date", "order_number"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

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
