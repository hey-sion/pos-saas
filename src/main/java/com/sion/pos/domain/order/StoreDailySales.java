package com.sion.pos.domain.order;

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
        name = "store_daily_sales",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_store_daily_sales_store_date",
                columnNames = {"store_id", "sales_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreDailySales extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "sales_date", nullable = false)
    private LocalDate salesDate;

    @Column(name = "sales_amount", nullable = false)
    private Long salesAmount;

    @Column(name = "order_count", nullable = false)
    private Integer orderCount;
}