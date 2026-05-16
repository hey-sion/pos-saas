package com.sion.pos.interfaces.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sion.pos.interfaces.api.ApiResponse;
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
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final int AMERICANO_PRICE = 4_000;
    private static final int LATTE_PRICE = 5_000;

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

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
        @DisplayName("유효한 요청이면, 201 Created와 RECEIVED 상태의 주문 정보를 반환한다.")
        void returnsCreated_whenValidRequest() {
            OrderCreateRequest request = new OrderCreateRequest(
                    storeId,
                    List.of(
                            new OrderCreateRequest.Line(americanoId, 1),
                            new OrderCreateRequest.Line(latteId, 2)
                    )
            );

            ResponseEntity<OrderCreateResponse> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), OrderCreateResponse.class);

            Long orderId = response.getBody().id();

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().storeId()).isEqualTo(storeId),
                    () -> assertThat(response.getBody().orderNumber()).isEqualTo(1),
                    () -> assertThat(response.getBody().status()).isEqualTo(Order.Status.RECEIVED),
                    () -> assertThat(response.getBody().totalAmount()).isEqualTo(AMERICANO_PRICE + LATTE_PRICE * 2),
                    () -> assertThat(order.getStoreId()).isEqualTo(storeId),
                    () -> assertThat(order.getOrderNumber()).isEqualTo(1),
                    () -> assertThat(order.getStatus()).isEqualTo(Order.Status.RECEIVED),
                    () -> assertThat(order.getTotalAmount()).isEqualTo(AMERICANO_PRICE + LATTE_PRICE * 2)
            );

            List<OrderItem> items = orderItemRepository.findAll().stream()
                                                       .filter(item -> item.getOrderId().equals(orderId))
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
        @DisplayName("주문 항목이 비어 있으면, 400 Bad Request를 반환한다.")
        void returnsBadRequest_whenItemsAreEmpty() {
            OrderCreateRequest request = new OrderCreateRequest(storeId, List.of());

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("주문 항목 수정 시, ")
    class UpdateItems {

        @Test
        @DisplayName("결제 완료 전 주문이면 200 OK와 수정된 총액을 반환한다.")
        void returnsOk_whenOrderIsNotPaid() {
            Long orderId = createOrder();
            OrderUpdateItemsRequest request = new OrderUpdateItemsRequest(
                    List.of(
                            new OrderUpdateItemsRequest.Line(americanoId, 2),
                            new OrderUpdateItemsRequest.Line(latteId, 1)
                    ));

            ResponseEntity<OrderCreateResponse> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/items", HttpMethod.PUT,
                            new HttpEntity<>(request), OrderCreateResponse.class);

            Order order = orderRepository.findById(orderId).orElseThrow();
            List<OrderItem> activeItems = orderItemRepository.findByOrderIdInAndDeletedAtIsNullOrderByIdAsc(List.of(orderId));
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().id()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().totalAmount()).isEqualTo(AMERICANO_PRICE * 2 + LATTE_PRICE),
                    () -> assertThat(order.getTotalAmount()).isEqualTo(AMERICANO_PRICE * 2 + LATTE_PRICE),
                    () -> assertThat(activeItems).hasSize(2)
            );
        }

        @Test
        @DisplayName("이미 결제 완료된 주문이면 409 Conflict를 반환한다.")
        void returnsConflict_whenOrderAlreadyPaid() {
            Long orderId = createOrder();
            paymentRepository.save(Payment.createOffline(orderId, Payment.Method.CASH, AMERICANO_PRICE, LocalDateTime.now()));
            OrderUpdateItemsRequest request = new OrderUpdateItemsRequest(
                    List.of(new OrderUpdateItemsRequest.Line(latteId, 1)));

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/items", HttpMethod.PUT,
                            new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("주문 상태 변경 시, ")
    class UpdateStatus {

        @Test
        @DisplayName("결제 완료 주문을 DELIVERED로 변경하면, 204 No Content를 반환하고 대기 목록에서 제외된다.")
        void returnsNoContentAndRemovesFromWaitingOrders_whenStatusDelivered() {
            Long orderId = createOrder();
            paymentRepository.save(Payment.createOffline(orderId, Payment.Method.CASH, AMERICANO_PRICE, LocalDateTime.now()));
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(Order.Status.DELIVERED);

            ResponseEntity<Void> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/status", HttpMethod.PATCH, new HttpEntity<>(request), Void.class);

            Order order = orderRepository.findById(orderId).orElseThrow();
            List<WaitingOrderResponse> waitingOrders = getWaitingOrders();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT),
                    () -> assertThat(order.getStatus()).isEqualTo(Order.Status.DELIVERED),
                    () -> assertThat(waitingOrders).extracting(WaitingOrderResponse::id).doesNotContain(orderId)
            );
        }

        @Test
        @DisplayName("결제 완료되지 않은 주문을 DELIVERED로 변경하려고 하면, 409 Conflict를 반환한다.")
        void returnsConflict_whenDeliveringUnpaidOrder() {
            Long orderId = createOrder();
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(Order.Status.DELIVERED);

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/status", HttpMethod.PATCH,
                            new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                    () -> assertThat(order.getStatus()).isEqualTo(Order.Status.RECEIVED)
            );
        }

        @Test
        @DisplayName("CANCELLED로 변경하면, 204 No Content를 반환하고 대기 목록에서 제외된다.")
        void returnsNoContentAndRemovesFromWaitingOrders_whenStatusCancelled() {
            Long orderId = createOrder();
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(Order.Status.CANCELLED);

            ResponseEntity<Void> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/status", HttpMethod.PATCH, new HttpEntity<>(request), Void.class);

            Order order = orderRepository.findById(orderId).orElseThrow();
            List<WaitingOrderResponse> waitingOrders = getWaitingOrders();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT),
                    () -> assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED),
                    () -> assertThat(waitingOrders).extracting(WaitingOrderResponse::id).doesNotContain(orderId)
            );
        }

        @Test
        @DisplayName("PENDING PG 결제가 있는 주문을 취소하면 결제 요청을 FAILED 처리한다.")
        void failsPendingPgPayment_whenStatusCancelled() {
            Long orderId = createOrder();
            Payment payment = paymentRepository.save(Payment.createPg(
                    orderId,
                    Payment.Provider.KAKAO_PAY,
                    AMERICANO_PRICE,
                    "pg-pending"));
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(Order.Status.CANCELLED);

            ResponseEntity<Void> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/status", HttpMethod.PATCH, new HttpEntity<>(request), Void.class);

            Payment persisted = paymentRepository.findById(payment.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT),
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.FAILED),
                    () -> assertThat(persisted.getFailReason()).isEqualTo("주문 취소로 기존 결제 요청 무효화")
            );
        }

        @Test
        @DisplayName("RECEIVED로 변경하려고 하면, 400 Bad Request를 반환한다.")
        void returnsBadRequest_whenStatusReceived() {
            Long orderId = createOrder();
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(Order.Status.RECEIVED);

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/status", HttpMethod.PATCH,
                            new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(order.getStatus()).isEqualTo(Order.Status.RECEIVED)
            );
        }

        @Test
        @DisplayName("이미 전달 완료된 주문을 취소하려고 하면, 409 Conflict를 반환한다.")
        void returnsConflict_whenCancellingDeliveredOrder() {
            Long orderId = createOrder();
            paymentRepository.save(Payment.createOffline(orderId, Payment.Method.CASH, AMERICANO_PRICE, LocalDateTime.now()));
            OrderStatusUpdateRequest deliveredRequest = new OrderStatusUpdateRequest(Order.Status.DELIVERED);
            testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/status", HttpMethod.PATCH, new HttpEntity<>(deliveredRequest), Void.class);

            OrderStatusUpdateRequest cancelledRequest = new OrderStatusUpdateRequest(Order.Status.CANCELLED);
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/status", HttpMethod.PATCH,
                            new HttpEntity<>(cancelledRequest), new ParameterizedTypeReference<>() {});

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                    () -> assertThat(order.getStatus()).isEqualTo(Order.Status.DELIVERED)
            );
        }

        @Test
        @DisplayName("이미 취소된 주문을 전달 완료하려고 하면, 409 Conflict를 반환한다.")
        void returnsConflict_whenDeliveringCancelledOrder() {
            Long orderId = createOrder();
            OrderStatusUpdateRequest cancelledRequest = new OrderStatusUpdateRequest(Order.Status.CANCELLED);
            testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/status", HttpMethod.PATCH, new HttpEntity<>(cancelledRequest), Void.class);

            OrderStatusUpdateRequest deliveredRequest = new OrderStatusUpdateRequest(Order.Status.DELIVERED);
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/status", HttpMethod.PATCH,
                            new HttpEntity<>(deliveredRequest), new ParameterizedTypeReference<>() {});

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                    () -> assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED)
            );
        }

        @Test
        @DisplayName("존재하지 않는 주문이면, 404 Not Found를 반환한다.")
        void returnsNotFound_whenOrderNotExists() {
            Long nonExistentOrderId = Long.MAX_VALUE;
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(Order.Status.DELIVERED);

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + nonExistentOrderId + "/status", HttpMethod.PATCH,
                            new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("일별 주문 요약 조회 시, ")
    class DailySummary {

        @Test
        @DisplayName("전체 주문을 주문번호 오름차순으로 반환한다.")
        void returnsOrdersOrderedByOrderNumber() {
            LocalDate today = LocalDate.now();
            Long firstOrderId = createOrder();
            Long secondOrderId = createOrder();
            Long thirdOrderId = createOrder();
            paymentRepository.save(Payment.createOffline(firstOrderId, Payment.Method.CASH, AMERICANO_PRICE, LocalDateTime.now()));
            updateOrderStatus(firstOrderId, Order.Status.DELIVERED);
            updateOrderStatus(secondOrderId, Order.Status.CANCELLED);

            ResponseEntity<DailyOrderSummaryResponse> response =
                    testRestTemplate.exchange(ENDPOINT + "/daily-summary?storeId=" + storeId + "&date=" + today,
                            HttpMethod.GET, HttpEntity.EMPTY, DailyOrderSummaryResponse.class);

            DailyOrderSummaryResponse body = response.getBody();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(body.date()).isEqualTo(today),
                    () -> assertThat(body.totalOrderCount()).isEqualTo(3),
                    () -> assertThat(body.orders()).extracting(DailyOrderSummaryResponse.OrderResponse::id)
                                                  .containsExactly(firstOrderId, secondOrderId, thirdOrderId),
                    () -> assertThat(body.orders()).extracting(DailyOrderSummaryResponse.OrderResponse::status)
                                                  .containsExactly("DELIVERED", "CANCELLED", "RECEIVED")
            );
        }

        @Test
        @DisplayName("전달 완료 주문만 매출로 집계한다.")
        void calculatesSalesOnlyWithDeliveredOrders() {
            LocalDate today = LocalDate.now();
            Long deliveredOrderId = createOrder();
            Long cancelledOrderId = createOrder();
            createOrder();
            paymentRepository.save(Payment.createOffline(deliveredOrderId, Payment.Method.CASH, AMERICANO_PRICE, LocalDateTime.now()));
            updateOrderStatus(deliveredOrderId, Order.Status.DELIVERED);
            updateOrderStatus(cancelledOrderId, Order.Status.CANCELLED);

            ResponseEntity<DailyOrderSummaryResponse> response =
                    testRestTemplate.exchange(ENDPOINT + "/daily-summary?storeId=" + storeId + "&date=" + today,
                            HttpMethod.GET, HttpEntity.EMPTY, DailyOrderSummaryResponse.class);

            DailyOrderSummaryResponse body = response.getBody();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(body.salesAmount()).isEqualTo(AMERICANO_PRICE),
                    () -> assertThat(body.salesOrderCount()).isEqualTo(1),
                    () -> assertThat(body.totalOrderCount()).isEqualTo(3)
            );
        }

        @Test
        @DisplayName("주문이 없으면, 매출과 주문 건수가 0이고 빈 주문 내역을 반환한다.")
        void returnsEmptySummary_whenOrdersDoNotExist() {
            LocalDate dateWithoutOrders = LocalDate.now().minusDays(1);

            ResponseEntity<DailyOrderSummaryResponse> response =
                    testRestTemplate.exchange(ENDPOINT + "/daily-summary?storeId=" + storeId + "&date=" + dateWithoutOrders,
                            HttpMethod.GET, HttpEntity.EMPTY, DailyOrderSummaryResponse.class);

            DailyOrderSummaryResponse body = response.getBody();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(body.salesAmount()).isZero(),
                    () -> assertThat(body.salesOrderCount()).isZero(),
                    () -> assertThat(body.totalOrderCount()).isZero(),
                    () -> assertThat(body.orders()).isEmpty()
            );
        }
    }

    private Long createOrder() {
        OrderCreateRequest request = new OrderCreateRequest(
                storeId,
                List.of(new OrderCreateRequest.Line(americanoId, 1))
        );

        ResponseEntity<OrderCreateResponse> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), OrderCreateResponse.class);

        return response.getBody().id();
    }

    private List<WaitingOrderResponse> getWaitingOrders() {
        ResponseEntity<List<WaitingOrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/waiting?storeId=" + storeId, HttpMethod.GET,
                        HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});

        return response.getBody();
    }

    private void updateOrderStatus(Long orderId, Order.Status status) {
        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(status);
        testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(request), Void.class);
    }
}
