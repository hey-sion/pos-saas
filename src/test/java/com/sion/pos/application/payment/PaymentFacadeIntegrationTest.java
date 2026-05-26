package com.sion.pos.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sion.pos.application.order.OrderCreateCommand;
import com.sion.pos.application.order.OrderFacade;
import com.sion.pos.application.order.OrderItemLine;
import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderRepository;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentGatewayResult;
import com.sion.pos.domain.payment.PaymentRepository;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import com.sion.pos.support.portone.FakePaymentGateway;
import com.sion.pos.support.portone.FakePaymentGatewayConfig;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(FakePaymentGatewayConfig.class)
class PaymentFacadeIntegrationTest {

    private static final int AMERICANO_PRICE = 4_000;

    @Autowired private PaymentFacade paymentFacade;
    @Autowired private OrderFacade orderFacade;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private FakePaymentGateway fakePaymentGateway;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long storeId;
    private Long americanoId;

    @BeforeEach
    void setUp() {
        Store store = storeRepository.save(Store.create("1번 테스트 매장", "010-1234-5678"));
        storeId = store.getId();
        americanoId = menuRepository.save(Menu.create(storeId, "아메리카노", AMERICANO_PRICE, 1)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("결제 생성 시, ")
    class CreatePayment {

        @Test
        @DisplayName("결제 수단이 CASH면 COMPLETED 상태로 생성된다")
        void createsCompletedOfflinePaymentForCash() {
            Order order = createOrderWith(americanoId, 1);

            PaymentCreateInfo info = paymentFacade.createPayment(storeId,
                    new PaymentCreateCommand(order.getId(), Payment.Method.CASH, null));

            Payment persisted = paymentRepository.findById(info.paymentId()).orElseThrow();
            assertThat(info.status()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(info.method()).isEqualTo(Payment.Method.CASH);
            assertThat(info.amount()).isEqualTo(AMERICANO_PRICE);
            assertThat(info.pg()).isNull();
            assertThat(persisted.getStatus()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(persisted.getChannel()).isEqualTo(Payment.Channel.OFFLINE);
            assertThat(persisted.getPaidAt()).isNotNull();
            assertThat(persisted.getProvider()).isNull();
        }

        @Test
        @DisplayName("결제 수단이 CARD면 COMPLETED 상태로 생성된다")
        void createsCompletedOfflinePaymentForCard() {
            Order order = createOrderWith(americanoId, 1);

            PaymentCreateInfo info = paymentFacade.createPayment(storeId,
                    new PaymentCreateCommand(order.getId(), Payment.Method.CARD, null));

            assertThat(info.status()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(info.method()).isEqualTo(Payment.Method.CARD);
            assertThat(info.pg()).isNull();
        }

        @Test
        @DisplayName("이미 결제 완료된 주문이면 CONFLICT 예외를 발생시킨다")
        void throwsConflictWhenAlreadyCompleted() {
            Order order = createOrderWith(americanoId, 1);
            paymentFacade.createPayment(storeId,
                    new PaymentCreateCommand(order.getId(), Payment.Method.CASH, null));

            expects(ErrorType.CONFLICT, () -> paymentFacade.createPayment(storeId,
                    new PaymentCreateCommand(order.getId(), Payment.Method.CARD, null)));
        }

        @Test
        @DisplayName("주문이 RECEIVED 상태가 아니면 CONFLICT 예외를 발생시킨다")
        void throwsConflictWhenOrderNotReceived() {
            Order order = createOrderWith(americanoId, 1);
            order.cancel();
            orderRepository.save(order);

            expects(ErrorType.CONFLICT, () -> paymentFacade.createPayment(storeId,
                    new PaymentCreateCommand(order.getId(), Payment.Method.CASH, null)));
        }

        @Test
        @DisplayName("존재하지 않는 주문이면 NOT_FOUND 예외를 발생시킨다")
        void throwsNotFoundWhenOrderNotExists() {
            expects(ErrorType.NOT_FOUND, () -> paymentFacade.createPayment(storeId,
                    new PaymentCreateCommand(Long.MAX_VALUE, Payment.Method.CASH, null)));
        }

        @Test
        @DisplayName("다른 매장 주문이면 NOT_FOUND 예외를 발생시킨다")
        void throwsNotFoundWhenOrderBelongsToOtherStore() {
            Order otherStoreOrder = createOtherStoreOrder();

            expects(ErrorType.NOT_FOUND, () -> paymentFacade.createPayment(storeId,
                    new PaymentCreateCommand(otherStoreOrder.getId(), Payment.Method.CASH, null)));
        }

        @Test
        @DisplayName("method가 null이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenMethodIsNull() {
            Order order = createOrderWith(americanoId, 1);

            expects(ErrorType.BAD_REQUEST, () -> paymentFacade.createPayment(storeId,
                    new PaymentCreateCommand(order.getId(), null, null)));
        }
    }

    @Nested
    @DisplayName("결제 검증 시, ")
    class Verify {

        @Test
        @DisplayName("PortOne이 PAID를 응답하면 COMPLETED 상태로 변경한다")
        void completesPaymentWhenPortOneRespondsPaid() {
            PaymentCreateInfo created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-abc", null));

            PaymentVerifyInfo info = paymentFacade.verify(storeId, created.paymentId());

            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertAll(
                    () -> assertThat(info.status()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(info.paidAt()).isNotNull(),
                    () -> assertThat(info.failReason()).isNull(),
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(persisted.getPgTransactionKey()).isEqualTo("tx-abc")
            );
        }

        @Test
        @DisplayName("PortOne이 FAILED를 응답하면 FAILED 상태로 변경한다")
        void failsPaymentWhenPortOneRespondsFailed() {
            PaymentCreateInfo created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.FAILED, null, null, "사용자 취소"));

            PaymentVerifyInfo info = paymentFacade.verify(storeId, created.paymentId());

            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertAll(
                    () -> assertThat(info.status()).isEqualTo(Payment.Status.FAILED),
                    () -> assertThat(info.failReason()).isEqualTo("사용자 취소"),
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.FAILED)
            );
        }

        @Test
        @DisplayName("PortOne이 PENDING을 응답하면 PENDING 상태를 유지한다")
        void keepsPendingWhenPortOneRespondsPending() {
            PaymentCreateInfo created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PENDING, null, null, null));

            PaymentVerifyInfo info = paymentFacade.verify(storeId, created.paymentId());

            assertThat(info.status()).isEqualTo(Payment.Status.PENDING);
        }

        @Test
        @DisplayName("이미 완료된 결제를 재검증하면 상태를 변경하지 않는다")
        void keepsCompletedPaymentWhenVerifyAgain() {
            PaymentCreateInfo created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-abc", null));
            paymentFacade.verify(storeId, created.paymentId());
            fakePaymentGateway.clear();

            PaymentVerifyInfo info = paymentFacade.verify(storeId, created.paymentId());

            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertAll(
                    () -> assertThat(info.status()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(persisted.getPgTransactionKey()).isEqualTo("tx-abc")
            );
        }

        @Test
        @DisplayName("결제 금액이 일치하지 않으면 PENDING 상태를 유지한다")
        void keepsPendingWhenAmountMismatched() {
            PaymentCreateInfo created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE + 1_000, "tx-abc", null));

            assertThatCode(() -> paymentFacade.verify(storeId, created.paymentId())).doesNotThrowAnyException();

            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertAll(
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.PENDING),
                    () -> assertThat(persisted.getPaidAt()).isNull(),
                    () -> assertThat(persisted.getPgTransactionKey()).isNull()
            );
        }

        @Test
        @DisplayName("다른 매장 결제이면 NOT_FOUND 예외를 발생시킨다")
        void throwsNotFoundWhenPaymentBelongsToOtherStore() {
            Payment payment = createOtherStorePgPayment();

            expects(ErrorType.NOT_FOUND, () -> paymentFacade.verify(storeId, payment.getId()));
        }
    }

    @Nested
    @DisplayName("PortOne 웹훅 처리 시, ")
    class HandlePortOneWebhook {

        @Test
        @DisplayName("PENDING 결제에 PAID 결과가 오면 COMPLETED로 전이한다")
        void completesPaymentWhenWebhookPaid() {
            PaymentCreateInfo created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-webhook", null));

            paymentFacade.handlePortOneWebhook("Transaction.Paid", created.pg().paymentId());

            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertAll(
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(persisted.getPaidAt()).isNotNull(),
                    () -> assertThat(persisted.getPgTransactionKey()).isEqualTo("tx-webhook")
            );
        }

        @Test
        @DisplayName("PENDING 결제에 FAILED 결과가 오면 FAILED로 전이한다")
        void failsPaymentWhenWebhookFailed() {
            PaymentCreateInfo created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.FAILED, null, null, "한도 초과"));

            paymentFacade.handlePortOneWebhook("Transaction.Failed", created.pg().paymentId());

            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertAll(
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.FAILED),
                    () -> assertThat(persisted.getFailReason()).isEqualTo("한도 초과")
            );
        }

        @Test
        @DisplayName("PG 재조회 결과가 PENDING이면 상태를 유지한다")
        void keepsPendingWhenGatewayPending() {
            PaymentCreateInfo created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PENDING, null, null, null));

            paymentFacade.handlePortOneWebhook("Transaction.Paid", created.pg().paymentId());

            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(Payment.Status.PENDING);
        }

        @Test
        @DisplayName("이미 COMPLETED된 결제는 PG 재조회 없이 무시한다")
        void ignoresAlreadyCompletedPaymentWithoutLookup() {
            PaymentCreateInfo created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-abc", null));
            paymentFacade.verify(storeId, created.paymentId());
            fakePaymentGateway.clear();

            assertThatCode(() -> paymentFacade.handlePortOneWebhook("Transaction.Paid", created.pg().paymentId()))
                    .doesNotThrowAnyException();

            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(Payment.Status.COMPLETED);
        }

        @Test
        @DisplayName("존재하지 않는 pgPaymentId면 예외 없이 반환한다")
        void returnsQuietlyWhenPaymentNotFound() {
            assertThatCode(() -> paymentFacade.handlePortOneWebhook("Transaction.Paid", "unknown-payment-id"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("처리 대상이 아닌 type이면 PG 재조회 없이 무시한다")
        void ignoresUnhandledType() {
            PaymentCreateInfo created = createPgPayment();

            assertThatCode(() -> paymentFacade.handlePortOneWebhook("Transaction.Cancelled", created.pg().paymentId()))
                    .doesNotThrowAnyException();

            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(Payment.Status.PENDING);
        }

        @Test
        @DisplayName("결제 금액이 일치하지 않으면 PENDING을 유지한다")
        void keepsPendingWhenAmountMismatched() {
            PaymentCreateInfo created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE + 1_000, "tx-abc", null));

            assertThatCode(() -> paymentFacade.handlePortOneWebhook("Transaction.Paid", created.pg().paymentId()))
                    .doesNotThrowAnyException();

            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertAll(
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.PENDING),
                    () -> assertThat(persisted.getPaidAt()).isNull(),
                    () -> assertThat(persisted.getPgTransactionKey()).isNull()
            );
        }
    }

    private Order createOrderWith(Long menuId, int quantity) {
        return orderFacade.createOrder(new OrderCreateCommand(
                storeId,
                List.of(new OrderItemLine(menuId, quantity))));
    }

    private PaymentCreateInfo createPgPayment() {
        Order order = createOrderWith(americanoId, 1);
        return paymentFacade.createPayment(storeId, new PaymentCreateCommand(
                order.getId(),
                Payment.Method.EASY_PAY,
                Payment.Provider.KAKAO_PAY));
    }

    private Order createOtherStoreOrder() {
        Store otherStore = storeRepository.save(Store.create("2번 테스트 매장", "010-9999-9999"));
        Long otherMenuId = menuRepository.save(Menu.create(otherStore.getId(), "다른 매장 메뉴", 9_000, 1)).getId();
        return orderFacade.createOrder(new OrderCreateCommand(
                otherStore.getId(),
                List.of(new OrderItemLine(otherMenuId, 1))));
    }

    private Payment createOtherStorePgPayment() {
        Order order = createOtherStoreOrder();
        return paymentRepository.save(Payment.createPg(
                order.getId(),
                Payment.Provider.KAKAO_PAY,
                order.getTotalAmount(),
                "other-store-payment"));
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}
