package com.sion.pos.interfaces.api.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sion.pos.application.store.StoreAccountService;
import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderRepository;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.interfaces.api.ApiResponse;
import com.sion.pos.interfaces.api.order.OrderCreateRequest;
import com.sion.pos.interfaces.api.order.OrderCreateResponse;
import com.sion.pos.interfaces.api.order.WaitingOrderResponse;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.security.ApiTestClient;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerOrderApiE2ETest {

    private static final String PASSWORD = "password1!";
    private static final int AMERICANO_PRICE = 4_000;
    private static final int LATTE_PRICE = 5_000;

    @LocalServerPort private int port;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private StoreAccountService storeAccountService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long storeId;
    private Long americanoId;
    private Long latteId;

    @BeforeEach
    void setUp() {
        Store store = storeRepository.save(Store.create("1번 테스트 매장", "010-1111-1111"));
        storeId = store.getId();
        americanoId = menuRepository.save(Menu.create(storeId, "아메리카노", AMERICANO_PRICE, 1)).getId();
        latteId = menuRepository.save(Menu.create(storeId, "라떼", LATTE_PRICE, 2)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("손님이 QR로 주문 생성 시, ")
    class CreateOrder {

        @Test
        @DisplayName("로그인 없이 주문을 생성하면 201 Created와 PAYMENT_PENDING 상태의 주문을 반환한다.")
        void createsPaymentPendingOrder_withoutLogin() {
            OrderCreateRequest request = new OrderCreateRequest(List.of(
                    new OrderCreateRequest.Line(americanoId, 1),
                    new OrderCreateRequest.Line(latteId, 2)));

            ResponseEntity<OrderCreateResponse> response = createOrder(storeId, request);

            OrderCreateResponse body = response.getBody();
            Order persisted = orderRepository.findById(body.id()).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(body.storeId()).isEqualTo(storeId),
                    () -> assertThat(body.status()).isEqualTo(Order.Status.PAYMENT_PENDING),
                    () -> assertThat(body.totalAmount()).isEqualTo(AMERICANO_PRICE + LATTE_PRICE * 2),
                    () -> assertThat(persisted.getStoreId()).isEqualTo(storeId),
                    () -> assertThat(persisted.getStatus()).isEqualTo(Order.Status.PAYMENT_PENDING)
            );
        }

        @Test
        @DisplayName("다른 매장 메뉴를 섞으면 404 Not Found를 반환한다.")
        void returnsNotFound_whenMenuBelongsToOtherStore() {
            Store otherStore = storeRepository.save(Store.create("2번 테스트 매장", "010-2222-2222"));
            Long otherMenuId = menuRepository.save(Menu.create(otherStore.getId(), "다른 매장 메뉴", 9_000, 1)).getId();
            OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderCreateRequest.Line(otherMenuId, 1)));

            ResponseEntity<String> response = ApiTestClient.plain(port).exchange(
                    endpoint(storeId), HttpMethod.POST, new HttpEntity<>(request), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("주문 항목이 비어 있으면 400 Bad Request를 반환한다.")
        void returnsBadRequest_whenItemsEmpty() {
            OrderCreateRequest request = new OrderCreateRequest(List.of());

            ResponseEntity<String> response = ApiTestClient.plain(port).exchange(
                    endpoint(storeId), HttpMethod.POST, new HttpEntity<>(request), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("한정 수량이 모두 팔린 메뉴를 주문하면 409와 OUT_OF_STOCK 코드를 반환한다.")
        void returnsOutOfStock_whenLimitedMenuSoldOut() {
            // Arrange
            int dailyLimit = 2;
            Menu limited = Menu.create(storeId, "오늘의 디저트", 6_000, 3);
            limited.changeDailyLimitQuantity(dailyLimit);
            Long limitedMenuId = menuRepository.save(limited).getId();
            createOrder(storeId, new OrderCreateRequest(List.of(
                    new OrderCreateRequest.Line(limitedMenuId, dailyLimit))));

            // Act
            ResponseEntity<ApiResponse<Object>> response = ApiTestClient.plain(port).exchange(
                    endpoint(storeId), HttpMethod.POST,
                    new HttpEntity<>(new OrderCreateRequest(List.of(new OrderCreateRequest.Line(limitedMenuId, 1)))),
                    new ParameterizedTypeReference<>() {});

            // Assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("OUT_OF_STOCK"),
                    () -> assertThat(orderRepository.findAll()).hasSize(1)
            );
        }

        @Test
        @DisplayName("결제 전(PAYMENT_PENDING) 주문은 사장님 대기 목록에 노출되지 않는다.")
        void doesNotAppearInWaitingOrdersBeforePayment() {
            OrderCreateRequest request = new OrderCreateRequest(List.of(
                    new OrderCreateRequest.Line(americanoId, 1)));
            Long orderId = createOrder(storeId, request).getBody().id();

            storeAccountService.register(storeId, "owner", PASSWORD);
            TestRestTemplate ownerClient = ApiTestClient.loggedIn(port, "owner", PASSWORD);
            ResponseEntity<List<WaitingOrderResponse>> waiting = ownerClient.exchange(
                    "/api/v1/orders/waiting", HttpMethod.GET, HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {});

            assertThat(waiting.getBody()).extracting(WaitingOrderResponse::id).doesNotContain(orderId);
        }
    }

    private ResponseEntity<OrderCreateResponse> createOrder(Long storeId, OrderCreateRequest request) {
        return ApiTestClient.plain(port).exchange(
                endpoint(storeId), HttpMethod.POST, new HttpEntity<>(request), OrderCreateResponse.class);
    }

    private String endpoint(Long storeId) {
        return "/api/v1/customer/stores/" + storeId + "/orders";
    }
}