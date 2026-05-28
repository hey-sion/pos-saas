package com.sion.pos.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
        @DisplayName("결제 대기(PAYMENT_PENDING) 상태로 주문을 생성한다")
        void createsOrderInPaymentPendingStatus() {
            Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);

            assertThat(order.getStatus()).isEqualTo(Order.Status.PAYMENT_PENDING);
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
    @DisplayName("주문 접수 처리 시, ")
    class MarkReceived {

        @Test
        @DisplayName("PAYMENT_PENDING 상태에서 호출하면 RECEIVED로 상태가 변경된다")
        void transitionsPaymentPendingToReceived() {
            Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);

            order.markReceived();

            assertThat(order.getStatus()).isEqualTo(Order.Status.RECEIVED);
        }

        @Test
        @DisplayName("이미 RECEIVED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyReceived() {
            Order order = receivedOrder();

            expects(ErrorType.CONFLICT, order::markReceived);
        }

        @Test
        @DisplayName("이미 DELIVERED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyDelivered() {
            Order order = deliveredOrder();

            expects(ErrorType.CONFLICT, order::markReceived);
        }

        @Test
        @DisplayName("이미 CANCELLED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyCancelled() {
            Order order = cancelledOrder();

            expects(ErrorType.CONFLICT, order::markReceived);
        }
    }

    @Nested
    @DisplayName("주문 제공 완료 처리 시, ")
    class Deliver {

        @Test
        @DisplayName("RECEIVED 상태에서 호출하면 DELIVERED로 상태가 변경된다")
        void transitionsReceivedToDelivered() {
            Order order = receivedOrder();

            order.deliver();

            assertThat(order.getStatus()).isEqualTo(Order.Status.DELIVERED);
        }

        @Test
        @DisplayName("결제 대기(PAYMENT_PENDING) 상태면 CONFLICT 예외를 던진다")
        void throwsWhenPaymentPending() {
            Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);

            expects(ErrorType.CONFLICT, order::deliver);
        }

        @Test
        @DisplayName("이미 DELIVERED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyDelivered() {
            Order order = deliveredOrder();

            expects(ErrorType.CONFLICT, order::deliver);
        }

        @Test
        @DisplayName("이미 CANCELLED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyCancelled() {
            Order order = cancelledOrder();

            expects(ErrorType.CONFLICT, order::deliver);
        }
    }

    @Nested
    @DisplayName("주문 취소 처리 시, ")
    class Cancel {

        @Test
        @DisplayName("PAYMENT_PENDING 상태에서 호출하면 CANCELLED로 상태가 변경된다")
        void transitionsPaymentPendingToCancelled() {
            Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        }

        @Test
        @DisplayName("RECEIVED 상태에서 호출하면 CANCELLED로 상태가 변경된다")
        void transitionsReceivedToCancelled() {
            Order order = receivedOrder();

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        }

        @Test
        @DisplayName("이미 DELIVERED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyDelivered() {
            Order order = deliveredOrder();

            expects(ErrorType.CONFLICT, order::cancel);
        }

        @Test
        @DisplayName("이미 CANCELLED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyCancelled() {
            Order order = cancelledOrder();

            expects(ErrorType.CONFLICT, order::cancel);
        }
    }

    @Nested
    @DisplayName("주문 금액 변경 시, ")
    class ChangeTotalAmount {

        @Test
        @DisplayName("PAYMENT_PENDING 상태에서 호출하면 총액이 변경된다")
        void changesTotalAmountWhenPaymentPending() {
            Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);

            order.changeTotalAmount(9_000);

            assertThat(order.getTotalAmount()).isEqualTo(9_000);
        }

        @Test
        @DisplayName("이미 RECEIVED(결제 완료) 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyReceived() {
            Order order = receivedOrder();

            expects(ErrorType.CONFLICT, () -> order.changeTotalAmount(9_000));
        }

        @Test
        @DisplayName("총액이 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenTotalAmountIsNotPositive() {
            Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);

            expects(ErrorType.BAD_REQUEST, () -> order.changeTotalAmount(null));
            expects(ErrorType.BAD_REQUEST, () -> order.changeTotalAmount(0));
            expects(ErrorType.BAD_REQUEST, () -> order.changeTotalAmount(-1));
        }
    }

    @Nested
    @DisplayName("결제 가능 여부 확인 시, ")
    class EnsurePayable {

        @Test
        @DisplayName("PAYMENT_PENDING 상태면 예외를 던지지 않는다")
        void doesNotThrowWhenPaymentPending() {
            Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);

            assertThatCode(order::ensurePayable).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("RECEIVED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenReceived() {
            Order order = receivedOrder();

            expects(ErrorType.CONFLICT, order::ensurePayable);
        }

        @Test
        @DisplayName("DELIVERED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenDelivered() {
            Order order = deliveredOrder();

            expects(ErrorType.CONFLICT, order::ensurePayable);
        }

        @Test
        @DisplayName("CANCELLED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenCancelled() {
            Order order = cancelledOrder();

            expects(ErrorType.CONFLICT, order::ensurePayable);
        }
    }

    private static Order receivedOrder() {
        Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);
        order.markReceived();
        return order;
    }

    private static Order deliveredOrder() {
        Order order = receivedOrder();
        order.deliver();
        return order;
    }

    private static Order cancelledOrder() {
        Order order = Order.create(STORE_ID, ORDER_DATE, ORDER_NUMBER, TOTAL_AMOUNT);
        order.cancel();
        return order;
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}
