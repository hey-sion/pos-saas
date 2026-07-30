package com.sion.pos.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuDailyStock;
import com.sion.pos.domain.menu.MenuDailyStockRepository;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderRepository;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LimitedStockOrderIntegrationTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MENU_PRICE = 6_000;
    private static final int DAILY_LIMIT = 10;

    @Autowired private OrderFacade orderFacade;
    @Autowired private OrderService orderService;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private MenuDailyStockRepository menuDailyStockRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long storeId;
    private Long limitedMenuId;
    private Long otherLimitedMenuId;
    private Long unlimitedMenuId;

    @BeforeEach
    void setUp() {
        Store store = storeRepository.save(Store.create("한정 메뉴 테스트 매장", "010-1234-5678"));
        storeId = store.getId();

        Menu limited = Menu.create(storeId, "오늘의 디저트", MENU_PRICE, 1);
        limited.changeDailyLimitQuantity(DAILY_LIMIT);
        limitedMenuId = menuRepository.save(limited).getId();

        Menu otherLimited = Menu.create(storeId, "오늘의 샌드위치", MENU_PRICE, 2);
        otherLimited.changeDailyLimitQuantity(DAILY_LIMIT);
        otherLimitedMenuId = menuRepository.save(otherLimited).getId();

        unlimitedMenuId = menuRepository.save(Menu.create(storeId, "아메리카노", MENU_PRICE, 3)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("한정 수량 메뉴를 주문할 때, ")
    class OrderLimitedMenu {

        @Test
        @DisplayName("남은 수량 안에서는 주문이 생성되고 판매 수량이 늘어난다")
        void createsOrderAndIncreasesSoldQuantity() {
            // Arrange & Act
            Order created = order(limitedMenuId, 3);

            // Assert
            assertThat(created.getId()).isNotNull();
            assertThat(stock(limitedMenuId).getSoldQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("첫 주문에 그날 재고 행이 생기고 메뉴 한도가 복사된다")
        void createsTodayStockRowOnFirstOrder() {
            // Arrange & Act
            order(limitedMenuId, 1);

            // Assert
            assertThat(stock(limitedMenuId).getLimitQuantity()).isEqualTo(DAILY_LIMIT);
            assertThat(stock(limitedMenuId).getSoldQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("한도와 정확히 같은 수량은 주문된다")
        void allowsOrderExactlyAtLimit() {
            // Arrange & Act
            Order created = order(limitedMenuId, DAILY_LIMIT);

            // Assert
            assertThat(created.getId()).isNotNull();
            assertThat(stock(limitedMenuId).getSoldQuantity()).isEqualTo(DAILY_LIMIT);
        }

        @Test
        @DisplayName("남은 수량보다 많이 주문하면 일부만 팔지 않고 전부 거부한다")
        void rejectsOrderExceedingRemaining() {
            // Arrange
            int remaining = 3;
            int soldBefore = DAILY_LIMIT - remaining;
            order(limitedMenuId, soldBefore);

            // Act & Assert
            expectsOutOfStock(() -> order(limitedMenuId, remaining + 1));
            assertThat(stock(limitedMenuId).getSoldQuantity()).isEqualTo(soldBefore);
        }

        @Test
        @DisplayName("이미 다 팔린 뒤 들어온 주문은 거부되고 저장되지 않는다")
        void rejectsOrderAfterSoldOut() {
            // Arrange
            Order soldOutOrder = sellOut();

            // Act & Assert
            expectsOutOfStock(() -> order(limitedMenuId, 1));
            assertThat(orderRepository.findAll()).extracting(Order::getId)
                                                 .containsExactly(soldOutOrder.getId());
            assertThat(stock(limitedMenuId).getSoldQuantity()).isEqualTo(DAILY_LIMIT);
        }

        @Test
        @DisplayName("첫 주문부터 한도를 넘기면 거부되고 재고 행도 생기지 않는다")
        void rejectsFirstOrderOverLimit() {
            // Arrange & Act & Assert
            expectsOutOfStock(() -> order(limitedMenuId, DAILY_LIMIT + 5));
            assertThat(findStock(limitedMenuId)).isEmpty();
            assertThat(orderRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("동시에 주문해도 한도 수량만큼만 성공한다")
        void sellsExactlyLimitQuantityConcurrently() {
            // Arrange
            int requestCount = 30;
            List<ErrorType> rejections = Collections.synchronizedList(new ArrayList<>());
            ExecutorService executorService = Executors.newFixedThreadPool(16);
            CountDownLatch startLatch = new CountDownLatch(1);

            List<CompletableFuture<Boolean>> futures = IntStream.range(0, requestCount)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        await(startLatch);
                        try {
                            order(limitedMenuId, 1);
                            return true;
                        } catch (PosApplicationException e) {
                            rejections.add(e.getErrorType());
                            return false;
                        }
                    }, executorService))
                    .toList();

            try {
                // Act
                startLatch.countDown();
                long succeeded = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();

                // Assert
                assertThat(succeeded).isEqualTo(DAILY_LIMIT);
                assertThat(rejections).hasSize(requestCount - DAILY_LIMIT)
                                      .containsOnly(ErrorType.OUT_OF_STOCK);
                assertThat(stock(limitedMenuId).getSoldQuantity()).isEqualTo(DAILY_LIMIT);
                assertThat(orderRepository.findAll()).hasSize(DAILY_LIMIT);
            } finally {
                executorService.shutdown();
            }
        }
    }

    @Nested
    @DisplayName("한도가 없는 메뉴를 주문할 때, ")
    class OrderUnlimitedMenu {

        @Test
        @DisplayName("수량 제한 없이 주문되고 재고 행도 생기지 않는다")
        void allowsAnyQuantityWithoutStockRow() {
            // Arrange & Act
            Order created = order(unlimitedMenuId, 100);

            // Assert
            assertThat(created.getId()).isNotNull();
            assertThat(findStock(unlimitedMenuId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("여러 메뉴를 한 번에 주문할 때, ")
    class OrderMultipleMenus {

        @Test
        @DisplayName("모두 남아 있으면 한정 메뉴마다 주문 수량만큼 차감된다")
        void deductsEachLimitedMenu() {
            // Arrange & Act
            order(new OrderItemLine(limitedMenuId, 2), new OrderItemLine(otherLimitedMenuId, 3));

            // Assert
            assertThat(stock(limitedMenuId).getSoldQuantity()).isEqualTo(2);
            assertThat(stock(otherLimitedMenuId).getSoldQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("한도가 없는 메뉴는 함께 담겨도 재고를 차지하지 않는다")
        void deductsOnlyLimitedMenu() {
            // Arrange & Act
            order(new OrderItemLine(limitedMenuId, 2), new OrderItemLine(unlimitedMenuId, 50));

            // Assert
            assertThat(stock(limitedMenuId).getSoldQuantity()).isEqualTo(2);
            assertThat(findStock(unlimitedMenuId)).isEmpty();
        }

        @Test
        @DisplayName("하나라도 품절이면 먼저 차감된 메뉴까지 되돌리고 주문 전체를 거부한다")
        void rollsBackDeductedMenuWhenAnotherIsSoldOut() {
            // Arrange — 재고는 menuId 순으로 깎이므로, 뒤에 깎이는 메뉴를 품절시켜야 앞의 메뉴가 깎인 뒤 실패한다
            Long rolledBackMenuId = limitedMenuId;
            Long soldOutMenuId = otherLimitedMenuId;
            order(soldOutMenuId, DAILY_LIMIT);

            // Act & Assert
            expectsOutOfStock(() -> order(new OrderItemLine(rolledBackMenuId, 3),
                                          new OrderItemLine(soldOutMenuId, 1)));

            // Assert
            assertThat(findStock(rolledBackMenuId)).isEmpty();
            assertThat(stock(soldOutMenuId).getSoldQuantity()).isEqualTo(DAILY_LIMIT);
        }

        @Test
        @DisplayName("한도가 없는 메뉴가 함께 담겨도 한정 메뉴가 불가하면 주문 전체가 저장되지 않는다")
        void rejectsWholeOrderWhenLimitedMenuUnavailable() {
            // Arrange
            Order soldOutOrder = sellOut();

            // Act & Assert
            expectsOutOfStock(() -> order(new OrderItemLine(limitedMenuId, 1),
                                          new OrderItemLine(unlimitedMenuId, 2)));
            assertThat(orderRepository.findAll()).extracting(Order::getId)
                                                 .containsExactly(soldOutOrder.getId());
        }

        @Test
        @DisplayName("품절과 수량 부족이 섞여 있어도 주문 전체를 거부한다")
        void rejectsWhenSoldOutAndShortageAreMixed() {
            // Arrange
            int remaining = 2;
            int otherSoldBefore = DAILY_LIMIT - remaining;
            order(limitedMenuId, DAILY_LIMIT);
            order(otherLimitedMenuId, otherSoldBefore);

            // Act & Assert
            expectsOutOfStock(() -> order(new OrderItemLine(limitedMenuId, 1),
                                          new OrderItemLine(otherLimitedMenuId, remaining + 1)));
            assertThat(stock(limitedMenuId).getSoldQuantity()).isEqualTo(DAILY_LIMIT);
            assertThat(stock(otherLimitedMenuId).getSoldQuantity()).isEqualTo(otherSoldBefore);
        }
    }

    @Nested
    @DisplayName("한정 수량 메뉴 주문을 취소할 때, ")
    class CancelLimitedMenuOrder {

        @Test
        @DisplayName("차감했던 수량이 판매 수량에서 빠진다")
        void restoresStockOnCancel() {
            // Arrange
            Order order = sellOut();

            // Act
            orderService.updateStatus(storeId, order.getId(), Order.Status.CANCELLED);

            // Assert
            assertThat(stock(limitedMenuId).getSoldQuantity()).isZero();
        }

        @Test
        @DisplayName("한도가 없는 메뉴가 섞여 있어도 한정 메뉴만 되돌아간다")
        void restoresOnlyLimitedMenuOnCancel() {
            // Arrange
            Order order = order(new OrderItemLine(limitedMenuId, 3), new OrderItemLine(unlimitedMenuId, 2));

            // Act
            orderService.updateStatus(storeId, order.getId(), Order.Status.CANCELLED);

            // Assert
            assertThat(stock(limitedMenuId).getSoldQuantity()).isZero();
            assertThat(findStock(unlimitedMenuId)).isEmpty();
        }

        @Test
        @DisplayName("한정 메뉴가 여럿이면 각각 되돌아간다")
        void restoresEveryLimitedMenu() {
            // Arrange
            Order order = order(new OrderItemLine(limitedMenuId, 2), new OrderItemLine(otherLimitedMenuId, 3));

            // Act
            orderService.updateStatus(storeId, order.getId(), Order.Status.CANCELLED);

            // Assert
            assertThat(stock(limitedMenuId).getSoldQuantity()).isZero();
            assertThat(stock(otherLimitedMenuId).getSoldQuantity()).isZero();
        }
    }

    @Nested
    @DisplayName("한정 수량 메뉴 주문을 수정할 때, ")
    class UpdateLimitedMenuOrder {

        @Test
        @DisplayName("바뀐 수량만큼만 재고를 차지한다")
        void deductsUpdatedQuantityOnly() {
            // Arrange
            Order order = order(limitedMenuId, 8);

            // Act
            changeOrderTo(order, limitedMenuId, 2);

            // Assert
            assertThat(stock(limitedMenuId).getSoldQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("다른 한정 메뉴로 바꾸면 원래 메뉴는 되돌아가고 새 메뉴가 차감된다")
        void movesDeductionWhenLimitedMenuReplaced() {
            // Arrange
            Order order = order(limitedMenuId, 4);

            // Act
            changeOrderTo(order, otherLimitedMenuId, 3);

            // Assert
            assertThat(stock(limitedMenuId).getSoldQuantity()).isZero();
            assertThat(stock(otherLimitedMenuId).getSoldQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("한도가 없는 메뉴로 바꾸면 한정 메뉴 차감분이 되돌아간다")
        void restoresWhenReplacedByUnlimitedMenu() {
            // Arrange
            Order order = order(limitedMenuId, 4);

            // Act
            changeOrderTo(order, unlimitedMenuId, 4);

            // Assert
            assertThat(stock(limitedMenuId).getSoldQuantity()).isZero();
            assertThat(findStock(unlimitedMenuId)).isEmpty();
        }

        @Test
        @DisplayName("남은 수량을 넘기게 수정하면 거부되고 판매 수량도 그대로다")
        void rejectsUpdateOverRemaining() {
            // Arrange
            int othersQuantity = 6;
            int myQuantity = 2;
            int myMaxQuantity = DAILY_LIMIT - othersQuantity;
            order(limitedMenuId, othersQuantity);
            Order myOrder = order(limitedMenuId, myQuantity);

            // Act & Assert
            expectsOutOfStock(() -> changeOrderTo(myOrder, limitedMenuId, myMaxQuantity + 1));
            assertThat(stock(limitedMenuId).getSoldQuantity()).isEqualTo(othersQuantity + myQuantity);
        }
    }

    private Order order(Long menuId, int quantity) {
        return order(new OrderItemLine(menuId, quantity));
    }

    private Order order(OrderItemLine... lines) {
        return orderFacade.createOrder(new OrderCreateCommand(storeId, List.of(lines)));
    }

    private Order sellOut() {
        return order(limitedMenuId, DAILY_LIMIT);
    }

    private void changeOrderTo(Order order, Long menuId, int quantity) {
        orderFacade.updateOrderItems(storeId, order.getId(),
                new OrderUpdateItemsCommand(List.of(new OrderItemLine(menuId, quantity))));
    }

    private void changeDailyLimit(int dailyLimitQuantity) {
        Menu limited = menuRepository.findById(limitedMenuId).orElseThrow();
        limited.changeDailyLimitQuantity(dailyLimitQuantity);
        menuRepository.save(limited);
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }

    private static void expectsOutOfStock(ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.OUT_OF_STOCK));
    }

    private MenuDailyStock stock(Long menuId) {
        return findStock(menuId).orElseThrow();
    }

    private Optional<MenuDailyStock> findStock(Long menuId) {
        return menuDailyStockRepository.findByStoreIdAndMenuIdAndStockDateAndDeletedAtIsNull(
                storeId, menuId, LocalDate.now(BUSINESS_ZONE));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}