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
}