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
    @DisplayName("오프라인 주문 생성 시, ")
    class CreateOffline {

        @Test
        @DisplayName("주문은 RECEIVED 상태로 저장되고 총액이 정확히 계산된다")
        void persistsOrderWithReceivedStatusAndTotalAmount() {
            OfflineOrderCreateCommand command = new OfflineOrderCreateCommand(
                    storeId,
                    List.of(new OfflineOrderCreateCommand.Line(americanoId, 1),
                            new OfflineOrderCreateCommand.Line(latteId, 2)),
                    Payment.Method.CASH);

            Order created = orderFacade.createOffline(command);

            Order persisted = orderRepository.findById(created.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(Order.Status.RECEIVED);
            assertThat(persisted.getStoreId()).isEqualTo(storeId);
            assertThat(persisted.getOrderNumber()).isEqualTo(1);
            assertThat(persisted.getTotalAmount()).isEqualTo(AMERICANO_PRICE + LATTE_PRICE * 2);
        }

        @Test
        @DisplayName("주문 항목에 주문 시점의 메뉴명/가격 스냅샷이 저장된다")
        void persistsOrderItemsWithMenuSnapshot() {
            OfflineOrderCreateCommand command = new OfflineOrderCreateCommand(
                    storeId,
                    List.of(new OfflineOrderCreateCommand.Line(americanoId, 1),
                            new OfflineOrderCreateCommand.Line(latteId, 2)),
                    Payment.Method.CASH);

            Order created = orderFacade.createOffline(command);

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
            OfflineOrderCreateCommand command = new OfflineOrderCreateCommand(
                    storeId,
                    List.of(new OfflineOrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CASH);

            Order created = orderFacade.createOffline(command);

            Payment payment = paymentRepository.findAll().stream()
                                               .filter(p -> p.getOrderId().equals(created.getId()))
                                               .findFirst().orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(payment.getMethod()).isEqualTo(Payment.Method.CASH);
            assertThat(payment.getChannel()).isEqualTo(Payment.Channel.OFFLINE);
            assertThat(payment.getProvider()).isNull();
            assertThat(payment.getPgPaymentId()).isNull();
            assertThat(payment.getAmount()).isEqualTo(created.getTotalAmount());
            assertThat(payment.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("CARD 결제 시 결제는 즉시 완료 상태가 된다")
        void completesPaymentImmediatelyForCard() {
            OfflineOrderCreateCommand command = new OfflineOrderCreateCommand(
                    storeId,
                    List.of(new OfflineOrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CARD);

            Order created = orderFacade.createOffline(command);

            Payment payment = paymentRepository.findAll().stream()
                                               .filter(p -> p.getOrderId().equals(created.getId()))
                                               .findFirst().orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(payment.getMethod()).isEqualTo(Payment.Method.CARD);
            assertThat(payment.getChannel()).isEqualTo(Payment.Channel.OFFLINE);
        }

        @Test
        @DisplayName("EASY_PAY 가 들어오면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenMethodIsEasyPay() {
            OfflineOrderCreateCommand command = new OfflineOrderCreateCommand(
                    storeId,
                    List.of(new OfflineOrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.EASY_PAY);

            expects(ErrorType.BAD_REQUEST, () -> orderFacade.createOffline(command));
        }

        @Test
        @DisplayName("같은 매장의 두 번째 주문은 orderNumber가 2가 된다")
        void incrementsOrderNumberOnSecondOrder() {
            OfflineOrderCreateCommand command = new OfflineOrderCreateCommand(
                    storeId,
                    List.of(new OfflineOrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CARD);

            Order first = orderFacade.createOffline(command);
            Order second = orderFacade.createOffline(command);

            assertThat(first.getOrderNumber()).isEqualTo(1);
            assertThat(second.getOrderNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("이전 날짜의 주문은 오늘의 orderNumber 채번에 영향이 없다")
        void previousDateOrderDoesNotAffectTodayNumber() {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            orderRepository.save(Order.create(storeId, yesterday, 5, AMERICANO_PRICE));

            OfflineOrderCreateCommand command = new OfflineOrderCreateCommand(
                    storeId,
                    List.of(new OfflineOrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CASH);

            Order today = orderFacade.createOffline(command);

            assertThat(today.getOrderNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("다른 매장의 주문은 우리 매장의 orderNumber 채번에 영향이 없다")
        void otherStoreOrderDoesNotAffectOurNumber() {
            Store other = storeRepository.save(Store.create("다른 매장", null));
            Long otherMenuId = menuRepository.save(Menu.create(other.getId(), "다른 메뉴", 3_000, 1)).getId();
            orderFacade.createOffline(new OfflineOrderCreateCommand(
                    other.getId(),
                    List.of(new OfflineOrderCreateCommand.Line(otherMenuId, 1)),
                    Payment.Method.CASH));

            Order ours = orderFacade.createOffline(new OfflineOrderCreateCommand(
                    storeId,
                    List.of(new OfflineOrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CASH));

            assertThat(ours.getOrderNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("주문 항목이 비어 있으면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenItemsEmpty() {
            OfflineOrderCreateCommand command = new OfflineOrderCreateCommand(storeId, List.of(), Payment.Method.CASH);

            expects(ErrorType.BAD_REQUEST, () -> orderFacade.createOffline(command));
        }

        @Test
        @DisplayName("중복된 메뉴가 포함되면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenDuplicatedMenu() {
            OfflineOrderCreateCommand command = new OfflineOrderCreateCommand(
                    storeId,
                    List.of(
                            new OfflineOrderCreateCommand.Line(americanoId, 1),
                            new OfflineOrderCreateCommand.Line(americanoId, 2)),
                    Payment.Method.CASH);

            expects(ErrorType.BAD_REQUEST, () -> orderFacade.createOffline(command));
        }

        @Test
        @DisplayName("다른 매장의 메뉴가 포함되면 NOT_FOUND 예외를 던진다")
        void throwsWhenMenuFromOtherStore() {
            Store other = storeRepository.save(Store.create("다른 매장", null));
            Long otherMenuId = menuRepository.save(Menu.create(other.getId(), "다른 메뉴", 3_000, 1)).getId();
            OfflineOrderCreateCommand command = new OfflineOrderCreateCommand(
                    storeId,
                    List.of(new OfflineOrderCreateCommand.Line(otherMenuId, 1)),
                    Payment.Method.CASH);

            expects(ErrorType.NOT_FOUND, () -> orderFacade.createOffline(command));
        }

        @Test
        @DisplayName("삭제된 메뉴가 포함되면 NOT_FOUND 예외를 던진다")
        void throwsWhenMenuDeleted() {
            Menu menu = menuRepository.findById(americanoId).orElseThrow();
            menu.delete();
            menuRepository.save(menu);
            OfflineOrderCreateCommand command = new OfflineOrderCreateCommand(
                    storeId,
                    List.of(new OfflineOrderCreateCommand.Line(americanoId, 1)),
                    Payment.Method.CASH);

            expects(ErrorType.NOT_FOUND, () -> orderFacade.createOffline(command));
        }
    }

    @Nested
    @DisplayName("간편결제 주문 생성 시, ")
    class CreateEasyPay {

        @Test
        @DisplayName("Order와 PENDING 상태의 PG 결제가 함께 생성된다")
        void persistsOrderAndPendingPgPayment() {
            EasyPayOrderCreateCommand command = new EasyPayOrderCreateCommand(
                    storeId,
                    List.of(new EasyPayOrderCreateCommand.Line(americanoId, 1),
                            new EasyPayOrderCreateCommand.Line(latteId, 2)),
                    Payment.Provider.KAKAO_PAY);

            EasyPayOrderCreateInfo info = orderFacade.createEasyPay(command);

            Order persisted = orderRepository.findById(info.orderId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(Order.Status.RECEIVED);
            assertThat(persisted.getTotalAmount()).isEqualTo(AMERICANO_PRICE + LATTE_PRICE * 2);

            Payment payment = paymentRepository.findById(info.paymentId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(Payment.Status.PENDING);
            assertThat(payment.getMethod()).isEqualTo(Payment.Method.EASY_PAY);
            assertThat(payment.getChannel()).isEqualTo(Payment.Channel.PG);
            assertThat(payment.getProvider()).isEqualTo(Payment.Provider.KAKAO_PAY);
            assertThat(payment.getPgPaymentId()).isNotBlank();
            assertThat(payment.getPaidAt()).isNull();
            assertThat(payment.getAmount()).isEqualTo(persisted.getTotalAmount());
        }

        @Test
        @DisplayName("응답에 포트원 호출용 정보가 포함된다")
        void returnsPortOneRequestInfo() {
            EasyPayOrderCreateCommand command = new EasyPayOrderCreateCommand(
                    storeId,
                    List.of(new EasyPayOrderCreateCommand.Line(americanoId, 1),
                            new EasyPayOrderCreateCommand.Line(latteId, 2)),
                    Payment.Provider.KAKAO_PAY);

            EasyPayOrderCreateInfo info = orderFacade.createEasyPay(command);

            assertThat(info.pgPaymentId()).isNotBlank();
            assertThat(info.provider()).isEqualTo(Payment.Provider.KAKAO_PAY);
            assertThat(info.totalAmount()).isEqualTo(AMERICANO_PRICE + LATTE_PRICE * 2);
            assertThat(info.orderName()).isEqualTo("아메리카노 외 1건");
            assertThat(info.portOneStoreId()).isNotBlank();
            assertThat(info.channelKey()).isNotBlank();
        }

        @Test
        @DisplayName("단일 항목 주문이면 orderName 은 메뉴명만 사용된다")
        void buildsOrderNameForSingleItem() {
            EasyPayOrderCreateCommand command = new EasyPayOrderCreateCommand(
                    storeId,
                    List.of(new EasyPayOrderCreateCommand.Line(americanoId, 1)),
                    Payment.Provider.KAKAO_PAY);

            EasyPayOrderCreateInfo info = orderFacade.createEasyPay(command);

            assertThat(info.orderName()).isEqualTo("아메리카노");
        }

        @Test
        @DisplayName("두 번 호출하면 서로 다른 pgPaymentId 가 발급된다")
        void issuesDistinctPgPaymentIds() {
            EasyPayOrderCreateCommand command = new EasyPayOrderCreateCommand(
                    storeId,
                    List.of(new EasyPayOrderCreateCommand.Line(americanoId, 1)),
                    Payment.Provider.KAKAO_PAY);

            EasyPayOrderCreateInfo first = orderFacade.createEasyPay(command);
            EasyPayOrderCreateInfo second = orderFacade.createEasyPay(command);

            assertThat(first.pgPaymentId()).isNotEqualTo(second.pgPaymentId());
        }

        @Test
        @DisplayName("provider 가 null 이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenProviderIsNull() {
            EasyPayOrderCreateCommand command = new EasyPayOrderCreateCommand(
                    storeId,
                    List.of(new EasyPayOrderCreateCommand.Line(americanoId, 1)),
                    null);

            expects(ErrorType.BAD_REQUEST, () -> orderFacade.createEasyPay(command));
        }

        @Test
        @DisplayName("주문 항목이 비어 있으면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenItemsEmpty() {
            EasyPayOrderCreateCommand command = new EasyPayOrderCreateCommand(
                    storeId, List.of(), Payment.Provider.KAKAO_PAY);

            expects(ErrorType.BAD_REQUEST, () -> orderFacade.createEasyPay(command));
        }

        @Test
        @DisplayName("다른 매장의 메뉴가 포함되면 NOT_FOUND 예외를 던진다")
        void throwsWhenMenuFromOtherStore() {
            Store other = storeRepository.save(Store.create("다른 매장", null));
            Long otherMenuId = menuRepository.save(Menu.create(other.getId(), "다른 메뉴", 3_000, 1)).getId();
            EasyPayOrderCreateCommand command = new EasyPayOrderCreateCommand(
                    storeId,
                    List.of(new EasyPayOrderCreateCommand.Line(otherMenuId, 1)),
                    Payment.Provider.KAKAO_PAY);

            expects(ErrorType.NOT_FOUND, () -> orderFacade.createEasyPay(command));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}