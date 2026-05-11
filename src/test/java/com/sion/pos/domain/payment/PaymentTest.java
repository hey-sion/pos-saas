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
    private static final String PG_PAYMENT_ID = "easypay-abcdef-1234";

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
        @DisplayName("결제 수단이 EASY_PAY면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenMethodIsEasyPay() {
            expects(ErrorType.BAD_REQUEST,
                    () -> Payment.createOffline(ORDER_ID, Payment.Method.EASY_PAY, AMOUNT, PAID_AT));
        }

        @Test
        @DisplayName("결제 수단이 null 이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenMethodIsNull() {
            expects(ErrorType.BAD_REQUEST,
                    () -> Payment.createOffline(ORDER_ID, null, AMOUNT, PAID_AT));
        }

        @Test
        @DisplayName("paidAt이 null 이면 BAD_REQUEST 예외를 발생시킨다")
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
    @DisplayName("PG 결제 생성 시, ")
    class CreatePg {

        @Test
        @DisplayName("PENDING 상태로 PG 결제를 생성한다")
        void createsPendingPgPayment() {
            Payment payment = Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, AMOUNT, PG_PAYMENT_ID);

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.PENDING);
            assertThat(payment.getMethod()).isEqualTo(Payment.Method.EASY_PAY);
            assertThat(payment.getChannel()).isEqualTo(Payment.Channel.PG);
            assertThat(payment.getProvider()).isEqualTo(Payment.Provider.KAKAO_PAY);
            assertThat(payment.getPgPaymentId()).isEqualTo(PG_PAYMENT_ID);
            assertThat(payment.getAmount()).isEqualTo(AMOUNT);
            assertThat(payment.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("provider가 null 이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenProviderIsNull() {
            expects(ErrorType.BAD_REQUEST, () -> Payment.createPg(ORDER_ID, null, AMOUNT, PG_PAYMENT_ID));
        }

        @Test
        @DisplayName("pgPaymentId가 null 또는 공백이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenPgPaymentIdIsBlank() {
            expects(ErrorType.BAD_REQUEST,
                    () -> Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, AMOUNT, null));
            expects(ErrorType.BAD_REQUEST,
                    () -> Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, AMOUNT, "  "));
        }

        @Test
        @DisplayName("orderId가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenOrderIdIsNotPositive() {
            expects(ErrorType.BAD_REQUEST,
                    () -> Payment.createPg(null, Payment.Provider.KAKAO_PAY, AMOUNT, PG_PAYMENT_ID));
            expects(ErrorType.BAD_REQUEST,
                    () -> Payment.createPg(0L, Payment.Provider.KAKAO_PAY, AMOUNT, PG_PAYMENT_ID));
        }

        @Test
        @DisplayName("amount가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenAmountIsNotPositive() {
            expects(ErrorType.BAD_REQUEST,
                    () -> Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, null, PG_PAYMENT_ID));
            expects(ErrorType.BAD_REQUEST,
                    () -> Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, 0, PG_PAYMENT_ID));
        }
    }

    @Nested
    @DisplayName("결제 완료 처리 시, ")
    class Complete {

        @Test
        @DisplayName("PG 결제 PENDING 상태에서 호출하면 COMPLETED로 변경되고 paidAt 이 기록된다")
        void transitionsPgPendingToCompleted() {
            Payment payment = Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, AMOUNT, PG_PAYMENT_ID);

            payment.complete(PAID_AT);

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(payment.getPaidAt()).isEqualTo(PAID_AT);
        }

        @Test
        @DisplayName("paidAt이 null 이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenPaidAtIsNull() {
            Payment payment = Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, AMOUNT, PG_PAYMENT_ID);

            expects(ErrorType.BAD_REQUEST, () -> payment.complete(null));
        }

        @Test
        @DisplayName("이미 COMPLETED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyCompleted() {
            Payment payment = Payment.createOffline(ORDER_ID, Payment.Method.CASH, AMOUNT, PAID_AT);

            expects(ErrorType.CONFLICT, () -> payment.complete(PAID_AT));
        }

        @Test
        @DisplayName("이미 FAILED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyFailed() {
            Payment payment = Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, AMOUNT, PG_PAYMENT_ID);
            payment.fail("PG 통신 오류");

            expects(ErrorType.CONFLICT, () -> payment.complete(PAID_AT));
        }
    }

    @Nested
    @DisplayName("결제 실패 처리 시, ")
    class Fail {

        @Test
        @DisplayName("PG 결제 PENDING 상태에서 호출하면 FAILED로 변경되고 사유가 기록된다")
        void transitionsPendingToFailed() {
            Payment payment = Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, AMOUNT, PG_PAYMENT_ID);

            payment.fail("PG 통신 오류");

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.FAILED);
            assertThat(payment.getFailReason()).isEqualTo("PG 통신 오류");
        }

        @Test
        @DisplayName("사유가 null 이어도 FAILED로 변경된다")
        void allowsNullReason() {
            Payment payment = Payment.createPg(ORDER_ID, Payment.Provider.KAKAO_PAY, AMOUNT, PG_PAYMENT_ID);

            payment.fail(null);

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.FAILED);
            assertThat(payment.getFailReason()).isNull();
        }

        @Test
        @DisplayName("이미 COMPLETED 상태면 CONFLICT 예외를 던진다")
        void throwsWhenAlreadyCompleted() {
            Payment payment = Payment.createOffline(ORDER_ID, Payment.Method.CASH, AMOUNT, PAID_AT);

            expects(ErrorType.CONFLICT, () -> payment.fail("늦은 실패"));
        }
    }

    @Nested
    @DisplayName("Provider의 PortOne 코드 변환은, ")
    class ProviderPortOneCode {

        @Test
        @DisplayName("KAKAO_PAY 는 KAKAOPAY 로 변환된다")
        void kakaoPay() {
            assertThat(Payment.Provider.KAKAO_PAY.toPortOneCode()).isEqualTo("KAKAOPAY");
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}