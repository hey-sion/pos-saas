package com.sion.pos.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Long ORDER_ID = 1L;
    private static final Integer AMOUNT = 5_000;
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 5, 8, 12, 30);

    @Nested
    @DisplayName("결제 생성 시, ")
    class Create {

        @Test
        @DisplayName("paidAt 이 null 이면 PENDING 상태로 결제를 생성한다")
        void createsPendingWhenPaidAtIsNull() {
            Payment payment = Payment.create(ORDER_ID, Payment.Method.EASY_PAY, Payment.Channel.PG, AMOUNT, null);

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.PENDING);
            assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(payment.getMethod()).isEqualTo(Payment.Method.EASY_PAY);
            assertThat(payment.getChannel()).isEqualTo(Payment.Channel.PG);
            assertThat(payment.getAmount()).isEqualTo(AMOUNT);
            assertThat(payment.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("paidAt 이 주어지면 COMPLETED 상태로 결제를 생성한다")
        void createsCompletedWhenPaidAtProvided() {
            Payment payment = Payment.create(ORDER_ID, Payment.Method.CASH, Payment.Channel.OFFLINE, AMOUNT, PAID_AT);

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(payment.getPaidAt()).isEqualTo(PAID_AT);
            assertThat(payment.getMethod()).isEqualTo(Payment.Method.CASH);
            assertThat(payment.getChannel()).isEqualTo(Payment.Channel.OFFLINE);
        }

        @Test
        @DisplayName("orderId 가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenOrderIdIsNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> Payment.create(null, Payment.Method.CASH, Payment.Channel.OFFLINE, AMOUNT, null));
            expects(ErrorType.BAD_REQUEST, () -> Payment.create(0L, Payment.Method.CASH, Payment.Channel.OFFLINE, AMOUNT, null));
            expects(ErrorType.BAD_REQUEST, () -> Payment.create(-1L, Payment.Method.CASH, Payment.Channel.OFFLINE, AMOUNT, null));
        }

        @Test
        @DisplayName("method 가 null 이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenMethodIsNull() {
            expects(ErrorType.BAD_REQUEST, () -> Payment.create(ORDER_ID, null, Payment.Channel.OFFLINE, AMOUNT, null));
        }

        @Test
        @DisplayName("channel 이 null 이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenChannelIsNull() {
            expects(ErrorType.BAD_REQUEST, () -> Payment.create(ORDER_ID, Payment.Method.CASH, null, AMOUNT, null));
        }

        @Test
        @DisplayName("amount 가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenAmountIsNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> Payment.create(ORDER_ID, Payment.Method.CASH, Payment.Channel.OFFLINE, null, null));
            expects(ErrorType.BAD_REQUEST, () -> Payment.create(ORDER_ID, Payment.Method.CASH, Payment.Channel.OFFLINE, 0, null));
            expects(ErrorType.BAD_REQUEST, () -> Payment.create(ORDER_ID, Payment.Method.CASH, Payment.Channel.OFFLINE, -1, null));
        }
    }

    @Nested
    @DisplayName("결제 완료 처리 시, ")
    class Complete {

        @Test
        @DisplayName("PENDING 상태에서 호출하면 COMPLETED 로 변경되고 paidAt 이 기록된다")
        void transitionsPendingToCompleted() {
            Payment payment = Payment.create(ORDER_ID, Payment.Method.EASY_PAY, Payment.Channel.PG, AMOUNT, null);

            payment.complete(PAID_AT);

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(payment.getPaidAt()).isEqualTo(PAID_AT);
        }

        @Test
        @DisplayName("paidAt 이 null 이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenPaidAtIsNull() {
            Payment payment = Payment.create(ORDER_ID, Payment.Method.EASY_PAY, Payment.Channel.PG, AMOUNT, null);

            expects(ErrorType.BAD_REQUEST, () -> payment.complete(null));
        }

        @Test
        @DisplayName("이미 COMPLETED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyCompleted() {
            Payment payment = Payment.create(ORDER_ID, Payment.Method.CASH, Payment.Channel.OFFLINE, AMOUNT, PAID_AT);

            expects(ErrorType.CONFLICT, () -> payment.complete(PAID_AT));
        }

        @Test
        @DisplayName("이미 FAILED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyFailed() {
            Payment payment = Payment.create(ORDER_ID, Payment.Method.EASY_PAY, Payment.Channel.PG, AMOUNT, null);
            payment.fail("PG 통신 오류");

            expects(ErrorType.CONFLICT, () -> payment.complete(PAID_AT));
        }
    }

    @Nested
    @DisplayName("결제 실패 처리 시, ")
    class Fail {

        @Test
        @DisplayName("PENDING 상태에서 호출하면 FAILED 로 변경되고 사유가 기록된다")
        void transitionsPendingToFailed() {
            Payment payment = Payment.create(ORDER_ID, Payment.Method.EASY_PAY, Payment.Channel.PG, AMOUNT, null);

            payment.fail("PG 통신 오류");

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.FAILED);
            assertThat(payment.getFailReason()).isEqualTo("PG 통신 오류");
        }

        @Test
        @DisplayName("사유가 null 이어도 FAILED 로 변경된다")
        void allowsNullReason() {
            Payment payment = Payment.create(ORDER_ID, Payment.Method.EASY_PAY, Payment.Channel.PG, AMOUNT, null);

            payment.fail(null);

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.FAILED);
            assertThat(payment.getFailReason()).isNull();
        }

        @Test
        @DisplayName("이미 COMPLETED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyCompleted() {
            Payment payment = Payment.create(ORDER_ID, Payment.Method.CASH, Payment.Channel.OFFLINE, AMOUNT, PAID_AT);

            expects(ErrorType.CONFLICT, () -> payment.fail("늦은 실패"));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}
