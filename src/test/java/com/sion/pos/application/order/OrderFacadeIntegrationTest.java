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
    class Create {

        @Test
        @DisplayName("주문은 RECEIVED 상태로 저장되고 총액이 정확히 계산된다")
        void persistsOrderWithReceivedStatusAndTotalAmount() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderCreateCommand.Line(americanoId, 1),
                            new OrderCreateCommand.Line(latteId, 2)),
                    Payment.Method.CASH);

            // act
            Order created = orderFacade.create(command);

            // assert
            Order persisted = orderRepository.findById(created.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(Order.Status.RECEIVED);
            assertThat(persisted.getStoreId()).isEqualTo(storeId);
            assertThat(persisted.getOrderNumber()).isEqualTo(1);
            assertThat(persisted.getTotalAmount()).isEqualTo(AMERICANO_PRICE + LATTE_PRICE * 2);
        }

        @Test
        @DisplayName("주문 항목에 주문 시점의 메뉴명/가격 스냅샷이 저장된다")
        void persistsOrderItemsWithMenuSnapshot() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderCreateCommand.Line(americanoId, 1),
                            new OrderCreateCommand.Line(latteId, 2)),
                    Payment.Method.CASH);

            // act
            Order created = orderFacade.create(command);

            // assert
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
        @DisplayName("CASH 결제 시 결제는 즉시 완료 상태가 된다")
        void completesPaymentImmediatelyForCash() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CASH);

            // act
            Order created = orderFacade.create(command);

            // assert
            Payment payment = paymentRepository.findAll().stream()
                                               .filter(p -> p.getOrderId().equals(created.getId()))
                                               .findFirst().orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(payment.getMethod()).isEqualTo(Payment.Method.CASH);
            assertThat(payment.getChannel()).isEqualTo(Payment.Channel.OFFLINE);
            assertThat(payment.getAmount()).isEqualTo(created.getTotalAmount());
            assertThat(payment.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("CARD 결제 시 결제는 즉시 완료 상태가 된다")
        void completesPaymentImmediatelyForCard() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CARD);

            // act
            Order created = orderFacade.create(command);

            // assert
            Payment payment = paymentRepository.findAll().stream()
                                               .filter(p -> p.getOrderId().equals(created.getId()))
                                               .findFirst().orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(payment.getMethod()).isEqualTo(Payment.Method.CARD);
            assertThat(payment.getChannel()).isEqualTo(Payment.Channel.OFFLINE);
            assertThat(payment.getAmount()).isEqualTo(created.getTotalAmount());
            assertThat(payment.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("같은 매장의 두 번째 주문은 orderNumber 가 2가 된다")
        void incrementsOrderNumberOnSecondOrder() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CARD);

            // act
            Order first = orderFacade.create(command);
            Order second = orderFacade.create(command);

            // assert
            assertThat(first.getOrderNumber()).isEqualTo(1);
            assertThat(second.getOrderNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("이전 날짜의 주문은 오늘의 orderNumber 채번에 영향이 없다")
        void previousDateOrderDoesNotAffectTodayNumber() {
            // arrange — 어제 5번까지 주문 받았던 상황을 시드
            LocalDate yesterday = LocalDate.now().minusDays(1);
            orderRepository.save(Order.create(storeId, yesterday, 5, AMERICANO_PRICE));

            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CASH);

            // act
            Order today = orderFacade.create(command);

            // assert
            assertThat(today.getOrderNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("다른 매장의 주문은 우리 매장의 orderNumber 채번에 영향이 없다")
        void otherStoreOrderDoesNotAffectOurNumber() {
            // arrange
            Store other = storeRepository.save(Store.create("다른 매장", null));
            Long otherMenuId = menuRepository.save(Menu.create(other.getId(), "다른 메뉴", 3_000, 1)).getId();
            orderFacade.create(new OrderCreateCommand(
                    other.getId(),
                    List.of(new OrderCreateCommand.Line(otherMenuId, 1)),
                    Payment.Method.CASH));

            // act
            Order ours = orderFacade.create(new OrderCreateCommand(
                    storeId,
                    List.of(new OrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CASH));

            // assert
            assertThat(ours.getOrderNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("주문 항목이 비어 있으면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenItemsEmpty() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(storeId, List.of(), Payment.Method.CASH);

            // act & assert
            expects(ErrorType.BAD_REQUEST, () -> orderFacade.create(command));
        }

        @Test
        @DisplayName("중복된 메뉴가 포함되면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenDuplicatedMenu() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(
                            new OrderCreateCommand.Line(americanoId, 1),
                            new OrderCreateCommand.Line(americanoId, 2)),
                    Payment.Method.CASH);

            // act & assert
            expects(ErrorType.BAD_REQUEST, () -> orderFacade.create(command));
        }

        @Test
        @DisplayName("다른 매장의 메뉴가 포함되면 NOT_FOUND 예외를 던진다")
        void throwsWhenMenuFromOtherStore() {
            // arrange
            Store other = storeRepository.save(Store.create("다른 매장", null));
            Long otherMenuId = menuRepository.save(Menu.create(other.getId(), "다른 메뉴", 3_000, 1)).getId();
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderCreateCommand.Line(otherMenuId, 1)),
                    Payment.Method.CASH);

            // act & assert
            expects(ErrorType.NOT_FOUND, () -> orderFacade.create(command));
        }

        @Test
        @DisplayName("삭제된 메뉴가 포함되면 NOT_FOUND 예외를 던진다")
        void throwsWhenMenuDeleted() {
            // arrange
            Menu menu = menuRepository.findById(americanoId).orElseThrow();
            menu.delete();
            menuRepository.save(menu);
            OrderCreateCommand command = new OrderCreateCommand(
                    storeId,
                    List.of(new OrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CASH);

            // act & assert
            expects(ErrorType.NOT_FOUND, () -> orderFacade.create(command));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}