package com.sion.pos.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import com.sion.pos.support.portone.FakePaymentGateway;
import com.sion.pos.support.portone.FakePaymentGatewayConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// 스케줄러는 기본 꺼짐(scheduling.enabled)이라 배치가 백그라운드로 돌며 테스트 데이터를 건드리지 않는다.
@SpringBootTest
@Import(FakePaymentGatewayConfig.class)
class PendingPaymentReconcilerTest {

    private static final int AMERICANO_PRICE = 4_000;

    @Autowired private PendingPaymentReconciler pendingPaymentReconciler;
    @Autowired private PaymentFacade paymentFacade;
    @Autowired private OrderFacade orderFacade;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private FakePaymentGateway fakePaymentGateway;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long storeId;
    private Long americanoId;

    @BeforeEach
    void setUp() {
        storeId = storeRepository.save(Store.create("1번 테스트 매장", "010-1234-5678")).getId();
        americanoId = menuRepository.save(Menu.create(storeId, "아메리카노", AMERICANO_PRICE, 1)).getId();
    }

    @AfterEach
    void tearDown() {
        fakePaymentGateway.clear();
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("미확정 결제 재조회 시, ")
    class Reconcile {

        @Test
        @DisplayName("유예 시간이 지난 결제가 PG에서 PAID면 COMPLETED로 전이하고 주문을 RECEIVED로 승격한다")
        void completesAndPromotesOrderWhenGatewayPaid() {
            // Arrange
            PaymentCreateInfo created = createAgedPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-batch", null));

            // Act
            pendingPaymentReconciler.reconcile();

            // Assert
            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            Order order = orderRepository.findById(created.orderId()).orElseThrow();
            assertAll(
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(persisted.getPgTransactionKey()).isEqualTo("tx-batch"),
                    () -> assertThat(order.getStatus()).isEqualTo(Order.Status.RECEIVED)
            );
        }

        @Test
        @DisplayName("유예 시간이 지나지 않은 결제는 건너뛴다")
        void skipsPaymentWithinGrace() {
            // Arrange
            PaymentCreateInfo created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-batch", null));

            // Act
            pendingPaymentReconciler.reconcile();

            // Assert
            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            Order order = orderRepository.findById(created.orderId()).orElseThrow();
            assertAll(
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.PENDING),
                    () -> assertThat(order.getStatus()).isEqualTo(Order.Status.PAYMENT_PENDING)
            );
        }

        @Test
        @DisplayName("PG 조회를 트랜잭션 밖에서 수행한다")
        void looksUpGatewayOutsideTransaction() {
            // Arrange
            PaymentCreateInfo created = createAgedPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-batch", null));

            // Act
            pendingPaymentReconciler.reconcile();

            // Assert
            assertThat(fakePaymentGateway.wasTransactionActiveOnLastLookup()).isFalse();
        }

        @Test
        @DisplayName("웹훅이 이미 확정한 결제는 다시 반영하지 않는다")
        void doesNotReapplyAlreadyCompletedPayment() {
            // Arrange
            PaymentCreateInfo created = createAgedPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-webhook", null));
            paymentFacade.handlePortOneWebhook("Transaction.Paid", created.pg().paymentId());
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-batch", null));

            // Act
            pendingPaymentReconciler.reconcile();

            // Assert
            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertThat(persisted.getPgTransactionKey()).isEqualTo("tx-webhook");
        }

        @Test
        @DisplayName("PG에서도 아직 PENDING이면 상태를 유지한다")
        void keepsPendingWhenGatewayPending() {
            // Arrange
            PaymentCreateInfo created = createAgedPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PENDING, null, null, null));

            // Act
            pendingPaymentReconciler.reconcile();

            // Assert
            Payment persisted = paymentRepository.findById(created.paymentId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(Payment.Status.PENDING);
        }

        @Test
        @DisplayName("한 건의 PG 조회가 실패해도 나머지 결제를 계속 처리한다")
        void continuesAfterGatewayFailure() {
            // Arrange — 앞 순서(더 오래된) 결제는 스텁이 없어 조회가 실패한다
            PaymentCreateInfo failing = createAgedPgPayment(10);
            PaymentCreateInfo succeeding = createAgedPgPayment(5);
            fakePaymentGateway.stub(succeeding.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-batch", null));

            // Act
            assertThatCode(() -> pendingPaymentReconciler.reconcile()).doesNotThrowAnyException();

            // Assert
            Payment failed = paymentRepository.findById(failing.paymentId()).orElseThrow();
            Payment completed = paymentRepository.findById(succeeding.paymentId()).orElseThrow();
            assertAll(
                    () -> assertThat(failed.getStatus()).isEqualTo(Payment.Status.PENDING),
                    () -> assertThat(completed.getStatus()).isEqualTo(Payment.Status.COMPLETED)
            );
        }
    }

    private PaymentCreateInfo createPgPayment() {
        Order order = orderFacade.createOrder(new OrderCreateCommand(
                storeId, List.of(new OrderItemLine(americanoId, 1))));
        return paymentFacade.createCustomerPayment(order.getId());
    }

    private PaymentCreateInfo createAgedPgPayment() {
        return createAgedPgPayment(5);
    }

    private PaymentCreateInfo createAgedPgPayment(int minutesAgo) {
        PaymentCreateInfo created = createPgPayment();
        jdbcTemplate.update("UPDATE payment SET created_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(minutesAgo), created.paymentId());
        return created;
    }
}