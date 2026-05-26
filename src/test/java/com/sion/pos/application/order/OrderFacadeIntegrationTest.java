package com.sion.pos.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderItem;
import com.sion.pos.domain.order.OrderItemRepository;
import com.sion.pos.domain.order.OrderRepository;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentRepository;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class OrderFacadeIntegrationTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final int AMERICANO_PRICE = 4_000;
    private static final int LATTE_PRICE = 5_000;

    private Long storeId;
    private Long americanoId;
    private Long latteId;

    @BeforeEach
    void setUp() {
        Store store = storeRepository.save(Store.create("1번 테스트 매장", "010-1234-5678"));
        storeId = store.getId();
        americanoId = menuRepository.save(Menu.create(storeId, "아메리카노", AMERICANO_PRICE, 1)).getId();
        latteId = menuRepository.save(Menu.create(storeId, "라떼", LATTE_PRICE, 2)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("주문 생성 시, ")
    class CreateOrder {

        @Test
        @DisplayName("주문은 RECEIVED 상태로 저장되고 총액이 정확히 계산된다")
        void persistsOrderWithReceivedStatusAndTotalAmount() {
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderItemLine(americanoId, 1),
                            new OrderItemLine(latteId, 2)));

            Order created = orderFacade.createOrder(command);

            Order persisted = orderRepository.findById(created.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(Order.Status.RECEIVED);
            assertThat(persisted.getStoreId()).isEqualTo(storeId);
            assertThat(persisted.getOrderNumber()).isEqualTo(1);
            assertThat(persisted.getTotalAmount()).isEqualTo(AMERICANO_PRICE + LATTE_PRICE * 2);
        }

        @Test
        @DisplayName("주문 항목에 주문 시점의 메뉴명/가격 스냅샷이 저장된다")
        void persistsOrderItemsWithMenuSnapshot() {
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderItemLine(americanoId, 1),
                            new OrderItemLine(latteId, 2)));

            Order created = orderFacade.createOrder(command);

            List<OrderItem> items = orderItemRepository.findAll().stream()
                                                       .filter(i -> i.getOrderId().equals(created.getId()))
                                                       .toList();
            assertThat(items).hasSize(2);
            assertThat(items).anySatisfy(item -> {
                assertThat(item.getMenuId()).isEqualTo(americanoId);
                assertThat(item.getMenuName()).isEqualTo("아메리카노");
                assertThat(item.getPrice()).isEqualTo(AMERICANO_PRICE);
                assertThat(item.getQuantity()).isEqualTo(1);
            });
            assertThat(items).anySatisfy(item -> {
                assertThat(item.getMenuId()).isEqualTo(latteId);
                assertThat(item.getMenuName()).isEqualTo("라떼");
                assertThat(item.getPrice()).isEqualTo(LATTE_PRICE);
                assertThat(item.getQuantity()).isEqualTo(2);
            });
        }

        @Test
        @DisplayName("같은 매장의 두 번째 주문은 orderNumber가 2가 된다")
        void incrementsOrderNumberOnSecondOrder() {
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderItemLine(americanoId, 1)));

            Order first = orderFacade.createOrder(command);
            Order second = orderFacade.createOrder(command);

            assertThat(first.getOrderNumber()).isEqualTo(1);
            assertThat(second.getOrderNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("이전 날짜의 주문은 오늘의 orderNumber 채번에 영향이 없다")
        void previousDateOrderDoesNotAffectTodayNumber() {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            orderRepository.save(Order.create(storeId, yesterday, 5, AMERICANO_PRICE));

            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderItemLine(americanoId, 1)));

            Order today = orderFacade.createOrder(command);

            assertThat(today.getOrderNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("다른 매장의 주문은 우리 매장의 orderNumber 채번에 영향이 없다")
        void otherStoreOrderDoesNotAffectOurNumber() {
            Store other = storeRepository.save(Store.create("다른 매장", null));
            Long otherMenuId = menuRepository.save(Menu.create(other.getId(), "다른 메뉴", 3_000, 1)).getId();
            orderFacade.createOrder(new OrderCreateCommand(
                    other.getId(),
                    List.of(new OrderItemLine(otherMenuId, 1))));

            Order ours = orderFacade.createOrder(new OrderCreateCommand(
                    storeId,
                    List.of(new OrderItemLine(americanoId, 1))));

            assertThat(ours.getOrderNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("주문 항목이 비어 있으면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenItemsEmpty() {
            OrderCreateCommand command = new OrderCreateCommand(storeId, List.of());

            expects(ErrorType.BAD_REQUEST, () -> orderFacade.createOrder(command));
        }

        @Test
        @DisplayName("중복된 메뉴가 포함되면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenDuplicatedMenu() {
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(
                            new OrderItemLine(americanoId, 1),
                            new OrderItemLine(americanoId, 2)));

            expects(ErrorType.BAD_REQUEST, () -> orderFacade.createOrder(command));
        }

        @Test
        @DisplayName("다른 매장의 메뉴가 포함되면 NOT_FOUND 예외를 던진다")
        void throwsWhenMenuFromOtherStore() {
            Store other = storeRepository.save(Store.create("다른 매장", null));
            Long otherMenuId = menuRepository.save(Menu.create(other.getId(), "다른 메뉴", 3_000, 1)).getId();
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderItemLine(otherMenuId, 1)));

            expects(ErrorType.NOT_FOUND, () -> orderFacade.createOrder(command));
        }

        @Test
        @DisplayName("삭제된 메뉴가 포함되면 NOT_FOUND 예외를 던진다")
        void throwsWhenMenuDeleted() {
            Menu menu = menuRepository.findById(americanoId).orElseThrow();
            menu.delete();
            menuRepository.save(menu);
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderItemLine(americanoId, 1)));

            expects(ErrorType.NOT_FOUND, () -> orderFacade.createOrder(command));
        }
    }

    @Nested
    @DisplayName("주문 항목 수정 시, ")
    class UpdateOrderItems {

        @Test
        @DisplayName("결제 완료 전 주문이면 항목과 총액을 교체한다")
        void replacesItemsAndTotalAmountBeforePaymentCompleted() {
            Order order = createOrderWith(new OrderItemLine(americanoId, 1));
            OrderUpdateItemsCommand command = new OrderUpdateItemsCommand(
                    List.of(
                            new OrderItemLine(americanoId, 2),
                            new OrderItemLine(latteId, 1)
                    ));

            Order updated = orderFacade.updateOrderItems(storeId, order.getId(), command);

            Order persisted = orderRepository.findById(updated.getId()).orElseThrow();
            List<OrderItem> activeItems = orderItemRepository.findByOrderIdInAndDeletedAtIsNullOrderByIdAsc(List.of(order.getId()));
            assertThat(persisted.getTotalAmount()).isEqualTo(AMERICANO_PRICE * 2 + LATTE_PRICE);
            assertThat(activeItems).hasSize(2);
            assertThat(activeItems).anySatisfy(item -> {
                assertThat(item.getMenuId()).isEqualTo(americanoId);
                assertThat(item.getQuantity()).isEqualTo(2);
            });
            assertThat(activeItems).anySatisfy(item -> {
                assertThat(item.getMenuId()).isEqualTo(latteId);
                assertThat(item.getQuantity()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName("이미 결제 완료된 주문이면 CONFLICT 예외를 발생시킨다")
        void throwsConflictWhenPaymentAlreadyCompleted() {
            Order order = createOrderWith(new OrderItemLine(americanoId, 1));
            paymentRepository.save(Payment.createOffline(order.getId(), Payment.Method.CASH, AMERICANO_PRICE, LocalDateTime.now()));
            OrderUpdateItemsCommand command = new OrderUpdateItemsCommand(
                    List.of(new OrderItemLine(latteId, 1)));

            expects(ErrorType.CONFLICT, () -> orderFacade.updateOrderItems(storeId, order.getId(), command));
        }

        @Test
        @DisplayName("기존 PENDING PG 결제가 있으면 FAILED 처리한다")
        void failsPendingPgPaymentWhenOrderItemsUpdated() {
            Order order = createOrderWith(new OrderItemLine(americanoId, 1));
            Payment pendingPg = paymentRepository.save(Payment.createPg(
                    order.getId(),
                    Payment.Provider.KAKAO_PAY,
                    AMERICANO_PRICE,
                    "pg-old"));
            OrderUpdateItemsCommand command = new OrderUpdateItemsCommand(
                    List.of(new OrderItemLine(latteId, 1)));

            orderFacade.updateOrderItems(storeId, order.getId(), command);

            Payment persisted = paymentRepository.findById(pendingPg.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(Payment.Status.FAILED);
            assertThat(persisted.getFailReason()).isEqualTo("주문 수정으로 기존 결제 요청 무효화");
        }

        @Test
        @DisplayName("취소된 주문이면 CONFLICT 예외를 발생시킨다")
        void throwsConflictWhenOrderCancelled() {
            Order order = createOrderWith(new OrderItemLine(americanoId, 1));
            order.cancel();
            orderRepository.save(order);
            OrderUpdateItemsCommand command = new OrderUpdateItemsCommand(
                    List.of(new OrderItemLine(latteId, 1)));

            expects(ErrorType.CONFLICT, () -> orderFacade.updateOrderItems(storeId, order.getId(), command));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }

    private Order createOrderWith(OrderItemLine... lines) {
        return orderFacade.createOrder(new OrderCreateCommand(storeId, List.of(lines)));
    }
}
