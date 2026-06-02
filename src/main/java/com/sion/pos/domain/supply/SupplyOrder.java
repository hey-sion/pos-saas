package com.sion.pos.domain.supply;

import com.sion.pos.domain.BaseEntity;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
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
@Table(name = "supply_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplyOrder extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REQUESTED;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    public static SupplyOrder create(Long storeId, Integer totalAmount) {
        if (storeId == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "storeId는 필수입니다.");
        }
        if (totalAmount == null || totalAmount <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "totalAmount는 1 이상이어야 합니다.");
        }

        SupplyOrder order = new SupplyOrder();
        order.storeId = storeId;
        order.totalAmount = totalAmount;
        order.status = Status.REQUESTED;
        return order;
    }

    /**
     * 발주 상태 흐름. 매장은 생성(REQUESTED)만 하고,
     * 이후 전이(확인→입금대기→발송대기→발송완료)는 본사 화면 작업 시 도메인 메서드로 추가한다.
     */
    public enum Status {
        REQUESTED,
        CONFIRMED,
        DEPOSITED,
        SHIPPED,
        CANCELLED
    }
}