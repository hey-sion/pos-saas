package com.sion.pos.application.order;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import com.sion.pos.application.payment.PaymentCreateCommand;
import com.sion.pos.application.payment.PaymentCreateInfo;
import com.sion.pos.application.payment.PaymentFacade;
import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentGatewayResult;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.portone.FakePaymentGateway;
import com.sion.pos.support.portone.FakePaymentGatewayConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(FakePaymentGatewayConfig.class)
class WaitingOrdersNotificationIntegrationTest {

    private static final int AMERICANO_PRICE = 4_000;

    @Autowired private OrderFacade orderFacade;
    @Autowired private OrderService orderService;
    @Autowired private PaymentFacade paymentFacade;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private FakePaymentGateway fakePaymentGateway;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @MockitoBean private WaitingOrdersNotifier notifier;

    private Long storeId;
    private Long americanoId;

    @BeforeEach
    void setUp() {
        storeId = storeRepository.save(Store.create("1번 테스트 매장", "010-1234-5678")).getId();
        americanoId = menuRepository.save(Menu.create(storeId, "아메리카노", AMERICANO_PRICE, 1)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("대기목록 변경 알림은, ")
    @Nested
    class WaitingOrdersNotification {

        @DisplayName("현금 즉시결제로 주문이 접수되면 발행된다.")
        @Test
        void notifiesOnCashPayment() {
            // arrange
            Order order = createOrder();

            // act
            paymentFacade.createPayment(storeId, new PaymentCreateCommand(order.getId(), Payment.Method.CASH, null));

            // assert
            verify(notifier).notifyUpdated(storeId);
        }

        @DisplayName("PG 결제가 확정되면 발행된다.")
        @Test
        void notifiesOnPgConfirmed() {
            // arrange
            Order order = createOrder();
            PaymentCreateInfo created = paymentFacade.createPayment(storeId, new PaymentCreateCommand(order.getId(), Payment.Method.EASY_PAY, Payment.Provider.KAKAO_PAY));
            fakePaymentGateway.stub(created.pg().paymentId(), new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-abc", null));

            // act
            paymentFacade.verify(storeId, created.paymentId());

            // assert
            verify(notifier).notifyUpdated(storeId);
        }

        @DisplayName("주문을 완료 처리하면 발행된다.")
        @Test
        void notifiesOnDeliver() {
            // arrange
            Order order = createOrder();
            paymentFacade.createPayment(storeId, new PaymentCreateCommand(order.getId(), Payment.Method.CASH, null));
            clearInvocations(notifier);

            // act
            orderService.updateStatus(storeId, order.getId(), Order.Status.DELIVERED);

            // assert
            verify(notifier).notifyUpdated(storeId);
        }

        @DisplayName("주문을 취소하면 발행된다.")
        @Test
        void notifiesOnCancel() {
            // arrange
            Order order = createOrder();

            // act
            orderService.updateStatus(storeId, order.getId(), Order.Status.CANCELLED);

            // assert
            verify(notifier).notifyUpdated(storeId);
        }
    }

    private Order createOrder() {
        return orderFacade.createOrder(new OrderCreateCommand(
                storeId, List.of(new OrderItemLine(americanoId, 1))));
    }
}