package com.sion.pos.domain.order;

import com.sion.pos.domain.BaseEntity;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
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
    private Status status = Status.RECEIVED;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    public static Order create(Long storeId, LocalDate orderDate, Integer orderNumber, Integer totalAmount) {
        if (storeId == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "storeId는 필수입니다.");
        }
        if (orderDate == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "orderDate는 필수입니다.");
        }
        if (orderNumber == null || orderNumber <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "orderNumber는 1 이상이어야 합니다.");
        }
        if (totalAmount == null || totalAmount <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "totalAmount는 1 이상이어야 합니다.");
        }

        Order order = new Order();
        order.storeId = storeId;
        order.orderDate = orderDate;
        order.orderNumber = orderNumber;
        order.totalAmount = totalAmount;
        order.status = Status.RECEIVED;
        return order;
    }

    public void deliver() {
        if (this.status != Status.RECEIVED) {
            throw new PosApplicationException(ErrorType.CONFLICT, "주문 접수 상태에서만 완료 처리 가능합니다. 현재 상태: " + this.status);
        }
        this.status = Status.DELIVERED;
    }

    public void cancel() {
        if (this.status != Status.RECEIVED) {
            throw new PosApplicationException(ErrorType.CONFLICT, "주문 접수 상태에서만 취소 처리 가능합니다. 현재 상태: " + this.status);
        }

        this.status = Status.CANCELLED;
    }

    public void changeTotalAmount(Integer totalAmount) {
        if (this.status != Status.RECEIVED) {
            throw new PosApplicationException(ErrorType.CONFLICT, "주문 접수 상태에서만 수정 가능합니다. 현재 상태: " + this.status);
        }
        if (totalAmount == null || totalAmount <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "totalAmount는 1 이상이어야 합니다.");
        }

        this.totalAmount = totalAmount;
    }

    public enum Status {
        RECEIVED,
        DELIVERED,
        CANCELLED
    }
}
