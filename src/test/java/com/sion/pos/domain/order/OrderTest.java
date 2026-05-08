package com.sion.pos.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.LocalDate;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Long STORE_ID = 1L;
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 5, 8);
    private static final Integer ORDER_NUMBER = 1;
    private static final Integer TOTAL_AMOUNT = 5_000;

    @Nested
    @DisplayName("주문 생성 시, ")
    class Create {

        @Test
        @DisplayName("RECEIVED 상태로 주문을 생성한다")
        void createsOrderInReceivedStatus() {
            Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);

            assertThat(order.getStatus()).isEqualTo(Order.Status.RECEIVED);
            assertThat(order.getStoreId()).isEqualTo(STORE_ID);
            assertThat(order.getOrderDate()).isEqualTo(ORDER_DATE);
            assertThat(order.getOrderNumber()).isEqualTo(ORDER_NUMBER);
            assertThat(order.getTotalAmount()).isEqualTo(TOTAL_AMOUNT);
        }

        @Test
        @DisplayName("storeId 가 null 이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenStoreIdIsNull() {
            expects(ErrorType.BAD_REQUEST,
                    () -> Order.create(null, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT));
        }

        @Test
        @DisplayName("orderDate 가 null 이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenOrderDateIsNull() {
            expects(ErrorType.BAD_REQUEST,
                    () -> Order.create(STORE_ID, null, ORDER_NUMBER, TOTAL_AMOUNT));
        }

        @Test
        @DisplayName("orderNumber 가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenOrderNumberIsNotPositive() {
            expects(ErrorType.BAD_REQUEST,
                    () -> Order.create(STORE_ID, ORDER_DATE, null, TOTAL_AMOUNT));
            expects(ErrorType.BAD_REQUEST,
                    () -> Order.create(STORE_ID, ORDER_DATE, 0, TOTAL_AMOUNT));
            expects(ErrorType.BAD_REQUEST,
                    () -> Order.create(STORE_ID, ORDER_DATE, -1, TOTAL_AMOUNT));
        }

        @Test
        @DisplayName("totalAmount 가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenTotalAmountIsNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, null));
            expects(ErrorType.BAD_REQUEST, () -> Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, 0));
            expects(ErrorType.BAD_REQUEST, () -> Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, -1));
        }
    }

    @Nested
    @DisplayName("주문 제공 완료 처리 시, ")
    class Deliver {

        @Test
        @DisplayName("RECEIVED 상태에서 호출하면 DELIVERED로 상태가 변경된다")
        void transitionsReceivedToDelivered() {
            Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);

            order.deliver();

            assertThat(order.getStatus()).isEqualTo(Order.Status.DELIVERED);
        }

        @Test
        @DisplayName("이미 DELIVERED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyDelivered() {
            Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);
            order.deliver();

            expects(ErrorType.CONFLICT, order::deliver);
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}