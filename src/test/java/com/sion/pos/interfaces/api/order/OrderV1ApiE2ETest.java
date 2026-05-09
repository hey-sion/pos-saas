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
        @DisplayName("유효한 요청이면, 201 Created와 생성된 주문 정보를 반환한다.")
        void returnsCreated_whenValidRequest() {
            OrderCreateRequest request = new OrderCreateRequest(
                    storeId,
                    List.of(
                            new OrderCreateRequest.Line(americanoId, 1),
                            new OrderCreateRequest.Line(latteId, 2)
                    ),
                    Payment.Method.CARD
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

            Payment payment = paymentRepository.findAll().stream()
                                               .filter(it -> it.getOrderId().equals(orderId))
                                               .findFirst()
                                               .orElseThrow();
            assertThat(payment.getMethod()).isEqualTo(Payment.Method.CARD);
            assertThat(payment.getChannel()).isEqualTo(Payment.Channel.OFFLINE);
            assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
            assertThat(payment.getAmount()).isEqualTo(order.getTotalAmount());
        }

        @Test
        @DisplayName("주문 항목이 비어 있으면, 400 Bad Request를 반환한다.")
        void returnsBadRequest_whenItemsAreEmpty() {
            OrderCreateRequest request = new OrderCreateRequest(storeId, List.of(), Payment.Method.CASH);

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("지원하지 않는 결제 방식이면, 400 Bad Request를 반환한다.")
        void returnsBadRequest_whenMethodUnsupported() {
            OrderCreateRequest request = new OrderCreateRequest(
                    storeId,
                    List.of(new OrderCreateRequest.Line(americanoId, 1)),
                    Payment.Method.EASY_PAY
            );

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("주문 상태 변경 시, ")
    class UpdateStatus {

        @Test
        @DisplayName("DELIVERED로 변경하면, 204 No Content를 반환하고 대기 목록에서 제외된다.")
        void returnsNoContentAndRemovesFromWaitingOrders_whenStatusDelivered() {
            Long orderId = createOrder(Payment.Method.CASH);
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
        @DisplayName("CANCELLED로 변경하면, 204 No Content를 반환하고 대기 목록에서 제외된다.")
        void returnsNoContentAndRemovesFromWaitingOrders_whenStatusCancelled() {
            Long orderId = createOrder(Payment.Method.CARD);
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
        @DisplayName("RECEIVED로 변경하려고 하면, 400 Bad Request를 반환한다.")
        void returnsBadRequest_whenStatusReceived() {
            Long orderId = createOrder(Payment.Method.CASH);
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
            Long orderId = createOrder(Payment.Method.CASH);
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
            Long orderId = createOrder(Payment.Method.CASH);
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

    private Long createOrder(Payment.Method method) {
        OrderCreateRequest request = new OrderCreateRequest(
                storeId,
                List.of(new OrderCreateRequest.Line(americanoId, 1)),
                method
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
}