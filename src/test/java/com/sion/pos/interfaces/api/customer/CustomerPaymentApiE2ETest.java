package com.sion.pos.interfaces.api.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sion.pos.application.order.OrderCreateCommand;
import com.sion.pos.application.order.OrderFacade;
import com.sion.pos.application.order.OrderItemLine;
import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.order.OrderRepository;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentGatewayResult;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.interfaces.api.ApiResponse;
import com.sion.pos.interfaces.api.payment.PaymentCreateResponse;
import com.sion.pos.interfaces.api.payment.PaymentVerifyResponse;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.portone.FakePaymentGateway;
import com.sion.pos.support.portone.FakePaymentGatewayConfig;
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
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakePaymentGatewayConfig.class)
class CustomerPaymentApiE2ETest {

    private static final int AMERICANO_PRICE = 4_000;

    @LocalServerPort private int port;
    @Autowired private OrderFacade orderFacade;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private FakePaymentGateway fakePaymentGateway;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private TestRestTemplate client;
    private Long storeId;
    private Long americanoId;

    @BeforeEach
    void setUp() {
        Store store = storeRepository.save(Store.create("1번 테스트 매장", "010-1234-5678"));
        storeId = store.getId();
        americanoId = menuRepository.save(Menu.create(storeId, "아메리카노", AMERICANO_PRICE, 1)).getId();
        client = ApiTestClient.plain(port);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("손님이 결제 생성 시, ")
    class CreatePayment {

        @Test
        @DisplayName("로그인 없이 결제를 생성하면 201 Created와 PENDING 상태 + 카카오페이 PG 파라미터를 반환한다.")
        void createsPendingKakaoPgPayment_withoutLogin() {
            Order order = createOrder();

            ResponseEntity<PaymentCreateResponse> response = client.exchange(
                    createEndpoint(order.getId()), HttpMethod.POST, HttpEntity.EMPTY, PaymentCreateResponse.class);

            PaymentCreateResponse body = response.getBody();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(body.payment().status()).isEqualTo(Payment.Status.PENDING),
                    () -> assertThat(body.payment().method()).isEqualTo(Payment.Method.EASY_PAY),
                    () -> assertThat(body.payment().amount()).isEqualTo(AMERICANO_PRICE),
                    () -> assertThat(body.pg()).isNotNull(),
                    () -> assertThat(body.pg().paymentId()).isNotBlank(),
                    () -> assertThat(body.pg().storeId()).isNotBlank(),
                    () -> assertThat(body.pg().channelKey()).isNotBlank(),
                    () -> assertThat(body.pg().orderName()).isNotBlank(),
                    () -> assertThat(body.pg().totalAmount()).isEqualTo(AMERICANO_PRICE),
                    () -> assertThat(body.pg().payMethod()).isEqualTo("EASY_PAY"),
                    () -> assertThat(body.pg().currency()).isEqualTo("KRW"),
                    () -> assertThat(body.pg().easyPay().easyPayProvider()).isEqualTo("KAKAOPAY")
            );
        }

        @Test
        @DisplayName("존재하지 않는 주문이면 404 Not Found를 반환한다.")
        void returnsNotFound_whenOrderNotExists() {
            Long nonExistingOrderId = Long.MAX_VALUE;
            ResponseEntity<ApiResponse<Void>> response = client.exchange(
                    createEndpoint(nonExistingOrderId), HttpMethod.POST, HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("손님이 결제 검증 시, ")
    class VerifyPayment {

        @Test
        @DisplayName("PG가 PAID를 응답하면 200 OK와 COMPLETED + 주문을 RECEIVED로 승격한다.")
        void completesAndPromotesOrder_whenPaid() {
            PaymentCreateResponse created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-customer", null));

            ResponseEntity<PaymentVerifyResponse> response = client.exchange(
                    verifyEndpoint(created.payment().id()), HttpMethod.POST, HttpEntity.EMPTY, PaymentVerifyResponse.class);

            PaymentVerifyResponse body = response.getBody();
            Order order = orderRepository.findById(created.payment().orderId()).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(body.status()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(order.getStatus()).isEqualTo(Order.Status.RECEIVED)
            );
        }

        @Test
        @DisplayName("존재하지 않는 결제 ID면 404 Not Found를 반환한다.")
        void returnsNotFound_whenPaymentNotExists() {
            Long nonExistingPaymentId = Long.MAX_VALUE;
            ResponseEntity<ApiResponse<Void>> response = client.exchange(
                    verifyEndpoint(nonExistingPaymentId), HttpMethod.POST, HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        private PaymentCreateResponse createPgPayment() {
            Order order = createOrder();
            return client.postForObject(createEndpoint(order.getId()), HttpEntity.EMPTY, PaymentCreateResponse.class);
        }
    }

    private Order createOrder() {
        return orderFacade.createOrder(new OrderCreateCommand(
                storeId, List.of(new OrderItemLine(americanoId, 1))));
    }

    private String createEndpoint(Long orderId) {
        return "/api/v1/customer/orders/" + orderId + "/payments";
    }

    private String verifyEndpoint(Long paymentId) {
        return "/api/v1/customer/payments/" + paymentId + "/verify";
    }
}