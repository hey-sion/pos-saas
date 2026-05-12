package com.sion.pos.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.application.order.OrderCreateCommand;
import com.sion.pos.application.order.OrderFacade;
import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderRepository;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentRepository;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentFacadeIntegrationTest {

    private static final int AMERICANO_PRICE = 4_000;

    @Autowired private PaymentFacade paymentFacade;
    @Autowired private OrderFacade orderFacade;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
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

            PaymentCreateInfo info = paymentFacade.createPayment(
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

            PaymentCreateInfo info = paymentFacade.createPayment(
                    new PaymentCreateCommand(order.getId(), Payment.Method.CARD, null));

            assertThat(info.status()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(info.method()).isEqualTo(Payment.Method.CARD);
            assertThat(info.pg()).isNull();
        }

        @Test
        @DisplayName("이미 결제 완료된 주문이면 CONFLICT 예외를 발생시킨다")
        void throwsConflictWhenAlreadyCompleted() {
            Order order = createOrderWith(americanoId, 1);
            paymentFacade.createPayment(
                    new PaymentCreateCommand(order.getId(), Payment.Method.CASH, null));

            expects(ErrorType.CONFLICT, () -> paymentFacade.createPayment(
                    new PaymentCreateCommand(order.getId(), Payment.Method.CARD, null)));
        }

        @Test
        @DisplayName("주문이 RECEIVED 상태가 아니면 CONFLICT 예외를 발생시킨다")
        void throwsConflictWhenOrderNotReceived() {
            Order order = createOrderWith(americanoId, 1);
            order.cancel();
            orderRepository.save(order);

            expects(ErrorType.CONFLICT, () -> paymentFacade.createPayment(
                    new PaymentCreateCommand(order.getId(), Payment.Method.CASH, null)));
        }

        @Test
        @DisplayName("존재하지 않는 주문이면 NOT_FOUND 예외를 발생시킨다")
        void throwsNotFoundWhenOrderNotExists() {
            expects(ErrorType.NOT_FOUND, () -> paymentFacade.createPayment(
                    new PaymentCreateCommand(Long.MAX_VALUE, Payment.Method.CASH, null)));
        }

        @Test
        @DisplayName("method가 null이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenMethodIsNull() {
            Order order = createOrderWith(americanoId, 1);

            expects(ErrorType.BAD_REQUEST, () -> paymentFacade.createPayment(
                    new PaymentCreateCommand(order.getId(), null, null)));
        }
    }

    private Order createOrderWith(Long menuId, int quantity) {
        return orderFacade.createOrder(new OrderCreateCommand(
                storeId,
                List.of(new OrderCreateCommand.Line(menuId, quantity))));
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}