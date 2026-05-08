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
        name = "order_number_sequence",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_number_sequence_store_date",
                columnNames = {"store_id", "order_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderNumberSequence extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "last_order_number", nullable = false)
    private Integer lastOrderNumber;

    public int next() {
        lastOrderNumber += 1;
        return lastOrderNumber;
    }
}