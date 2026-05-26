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
    @DisplayName("오프라인 결제 생성 시, ")
    class CreateOffline {

        @Test
        @DisplayName("결제 수단이 CASH면 COMPLETED 상태의 결제를 생성한다")
        void createsCompletedCashPayment() {
            Payment payment = Payment.createOffline(ORDER_ID, Payment.Method.CASH, AMOUNT, PAID_AT);

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(payment.getMethod()).isEqualTo(Payment.Method.CASH);
            assertThat(payment.getChannel()).isEqualTo(Payment.Channel.OFFLINE);
            assertThat(payment.getAmount()).isEqualTo(AMOUNT);
            assertThat(payment.getPaidAt()).isEqualTo(PAID_AT);
            assertThat(payment.getProvider()).isNull();
            assertThat(payment.getPgPaymentId()).isNull();
        }

        @Test
        @DisplayName("결제 수단이 CARD면 COMPLETED 상태의 결제를 생성한다")
        void createsCompletedCardPayment() {
            Payment payment = Payment.createOffline(ORDER_ID, Payment.Method.CARD, AMOUNT, PAID_AT);

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(payment.getMethod()).isEqualTo(Payment.Method.CARD);
            assertThat(payment.getChannel()).isEqualTo(Payment.Channel.OFFLINE);
        }

        @Test
        @DisplayName("결제 수단이 null이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenMethodIsNull() {
            expects(ErrorType.BAD_REQUEST,
                    () -> Payment.createOffline(ORDER_ID, null, AMOUNT, PAID_AT));
        }

        @Test
        @DisplayName("paidAt이 null이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenPaidAtIsNull() {
            expects(ErrorType.BAD_REQUEST,
                    () -> Payment.createOffline(ORDER_ID, Payment.Method.CASH, AMOUNT, null));
        }

        @Test
        @DisplayName("orderId가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenOrderIdIsNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> Payment.createOffline(null, Payment.Method.CASH, AMOUNT, PAID_AT));
            expects(ErrorType.BAD_REQUEST, () -> Payment.createOffline(0L, Payment.Method.CASH, AMOUNT, PAID_AT));
        }

        @Test
        @DisplayName("amount가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenAmountIsNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> Payment.createOffline(ORDER_ID, Payment.Method.CASH, null, PAID_AT));
            expects(ErrorType.BAD_REQUEST, () -> Payment.createOffline(ORDER_ID, Payment.Method.CASH, 0, PAID_AT));
        }
    }

    @Nested
    @DisplayName("상태 질의 시, ")
    class StateQuery {

        @Test
        @DisplayName("PG 결제는 isPgChannel이 true, 오프라인 결제는 false다")
        void answersIsPgChannel() {
            Payment pg = Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, AMOUNT, "pg-123");
            Payment offline = Payment.createOffline(ORDER_ID, Payment.Method.CASH, AMOUNT, PAID_AT);

            assertThat(pg.isPgChannel()).isTrue();
            assertThat(offline.isPgChannel()).isFalse();
        }

        @Test
        @DisplayName("생성 직후 PG 결제는 isPending이 true, 완료된 결제는 false다")
        void answersIsPending() {
            Payment pending = Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, AMOUNT, "pg-123");
            Payment completed = Payment.createOffline(ORDER_ID, Payment.Method.CASH, AMOUNT, PAID_AT);

            assertThat(pending.isPending()).isTrue();
            assertThat(completed.isPending()).isFalse();
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}