package com.sion.pos.domain.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SupplyOrderTest {

    private static final Long STORE_ID = 1L;
    private static final Integer TOTAL_AMOUNT = 187_000;

    @Nested
    @DisplayName("발주 생성 시, ")
    class Create {

        @Test
        @DisplayName("주문확인중(REQUESTED) 상태로 발주를 생성한다")
        void createsSupplyOrderInRequestedStatus() {
            SupplyOrder order = SupplyOrder.create(STORE_ID, TOTAL_AMOUNT);

            assertThat(order.getStatus()).isEqualTo(SupplyOrder.Status.REQUESTED);
            assertThat(order.getStoreId()).isEqualTo(STORE_ID);
            assertThat(order.getTotalAmount()).isEqualTo(TOTAL_AMOUNT);
        }

        @Test
        @DisplayName("storeId가 null이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenStoreIdIsNull() {
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrder.create(null, TOTAL_AMOUNT));
        }

        @Test
        @DisplayName("totalAmount가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenTotalAmountIsNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrder.create(STORE_ID, null));
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrder.create(STORE_ID, 0));
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrder.create(STORE_ID, -1));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}