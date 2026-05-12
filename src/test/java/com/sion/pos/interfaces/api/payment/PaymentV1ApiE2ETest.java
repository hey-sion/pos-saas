package com.sion.pos.interfaces.api.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sion.pos.application.order.OrderCreateCommand;
import com.sion.pos.application.order.OrderFacade;
import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentRepository;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.interfaces.api.ApiResponse;
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
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final int AMERICANO_PRICE = 4_000;

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private OrderFacade orderFacade;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
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
    class Create {

        @Test
        @DisplayName("오프라인 결제(CASH)는 201 Created와 COMPLETED 상태 + pg=null 응답을 반환한다.")
        void returnsCreated_whenOfflineCash() {
            Order order = createOrder(americanoId, 1);
            PaymentCreateRequest request = new PaymentCreateRequest(order.getId(), Payment.Method.CASH, null);

            ResponseEntity<PaymentCreateResponse> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(request), PaymentCreateResponse.class);

            PaymentCreateResponse body = response.getBody();
            Payment persisted = paymentRepository.findById(body.payment().id()).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(body.payment().status()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(body.payment().method()).isEqualTo(Payment.Method.CASH),
                    () -> assertThat(body.payment().amount()).isEqualTo(AMERICANO_PRICE),
                    () -> assertThat(body.pg()).isNull(),
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(persisted.getChannel()).isEqualTo(Payment.Channel.OFFLINE)
            );
        }

        @Test
        @DisplayName("orderId가 누락되면 400 Bad Request를 반환한다.")
        void returnsBadRequest_whenOrderIdMissing() {
            PaymentCreateRequest request = new PaymentCreateRequest(null, Payment.Method.CASH, null);

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("method가 누락되면 400 Bad Request를 반환한다.")
        void returnsBadRequest_whenMethodMissing() {
            Order order = createOrder(americanoId, 1);
            PaymentCreateRequest request = new PaymentCreateRequest(order.getId(), null, null);

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("이미 결제 완료된 주문에 다시 결제하면 409 Conflict를 반환한다.")
        void returnsConflict_whenAlreadyPaid() {
            Order order = createOrder(americanoId, 1);
            PaymentCreateRequest first = new PaymentCreateRequest(order.getId(), Payment.Method.CASH, null);
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(first), PaymentCreateResponse.class);

            PaymentCreateRequest second = new PaymentCreateRequest(order.getId(), Payment.Method.CARD, null);

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(second), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("존재하지 않는 주문이면 404 Not Found를 반환한다.")
        void returnsNotFound_whenOrderNotExists() {
            PaymentCreateRequest request = new PaymentCreateRequest(Long.MAX_VALUE, Payment.Method.CASH, null);

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    private Order createOrder(Long menuId, int quantity) {
        return orderFacade.createOrder(new OrderCreateCommand(
                storeId,
                List.of(new OrderCreateCommand.Line(menuId, quantity))));
    }
}