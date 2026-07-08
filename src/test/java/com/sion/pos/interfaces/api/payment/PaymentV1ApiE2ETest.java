package com.sion.pos.interfaces.api.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sion.pos.application.order.OrderCreateCommand;
import com.sion.pos.application.order.OrderFacade;
import com.sion.pos.application.order.OrderItemLine;
import com.sion.pos.application.store.StoreAccountService;
import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.order.Order;
import com.sion.pos.domain.payment.Payment;
import com.sion.pos.domain.payment.PaymentGatewayResult;
import com.sion.pos.domain.payment.PaymentRepository;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.interfaces.api.ApiResponse;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.security.ApiTestClient;
import com.sion.pos.support.portone.FakePaymentGateway;
import com.sion.pos.support.portone.FakePaymentGatewayConfig;
import com.sion.pos.support.portone.PortOneProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakePaymentGatewayConfig.class)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final int AMERICANO_PRICE = 4_000;

    @LocalServerPort private int port;
    @Autowired private OrderFacade orderFacade;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private FakePaymentGateway fakePaymentGateway;
    @Autowired private PortOneProperties portOneProperties;
    @Autowired private StoreAccountService storeAccountService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private TestRestTemplate testRestTemplate;
    private Long storeId;
    private Long americanoId;

    @BeforeEach
    void setUp() {
        Store store = Store.create("1번 테스트 매장", "010-1234-5678");
        storeId = storeRepository.save(store).getId();
        americanoId = menuRepository.save(Menu.create(storeId, "아메리카노", AMERICANO_PRICE, 1)).getId();
        storeAccountService.register(storeId, "owner", "password1!");
        testRestTemplate = ApiTestClient.loggedIn(port, "owner", "password1!");
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

        @Test
        @DisplayName("다른 매장 주문이면 404 Not Found를 반환한다.")
        void returnsNotFound_whenOrderBelongsToOtherStore() {
            Order otherStoreOrder = createOtherStoreOrder();
            PaymentCreateRequest request = new PaymentCreateRequest(otherStoreOrder.getId(), Payment.Method.CASH, null);

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("결제 검증 시, ")
    class Verify {

        @Test
        @DisplayName("PortOne이 PAID를 응답하면 200 OK와 COMPLETED 상태를 반환한다.")
        void returnsCompleted_whenPortOneRespondsPaid() {
            PaymentCreateResponse created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-abc", null));

            ResponseEntity<PaymentVerifyResponse> response =
                    testRestTemplate.exchange(verifyEndpoint(created.payment().id()), HttpMethod.POST,
                            HttpEntity.EMPTY, PaymentVerifyResponse.class);

            PaymentVerifyResponse body = response.getBody();
            Payment persisted = paymentRepository.findById(created.payment().id()).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(body.status()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(body.paidAt()).isNotNull(),
                    () -> assertThat(body.failReason()).isNull(),
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(persisted.getPgTransactionKey()).isEqualTo("tx-abc")
            );
        }

        @Test
        @DisplayName("PortOne이 FAILED를 응답하면 200 OK와 FAILED 상태 + failReason을 반환한다.")
        void returnsFailed_whenPortOneRespondsFailed() {
            PaymentCreateResponse created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.FAILED, null, null, "사용자 취소"));

            ResponseEntity<PaymentVerifyResponse> response =
                    testRestTemplate.exchange(verifyEndpoint(created.payment().id()), HttpMethod.POST,
                            HttpEntity.EMPTY, PaymentVerifyResponse.class);

            PaymentVerifyResponse body = response.getBody();
            Payment persisted = paymentRepository.findById(created.payment().id()).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(body.status()).isEqualTo(Payment.Status.FAILED),
                    () -> assertThat(body.failReason()).isEqualTo("사용자 취소"),
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.FAILED)
            );
        }

        @Test
        @DisplayName("PortOne이 PENDING을 응답하면 PENDING 상태를 유지한다.")
        void returnsPending_whenPortOneRespondsPending() {
            PaymentCreateResponse created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PENDING, null, null, null));

            ResponseEntity<PaymentVerifyResponse> response =
                    testRestTemplate.exchange(verifyEndpoint(created.payment().id()), HttpMethod.POST,
                            HttpEntity.EMPTY, PaymentVerifyResponse.class);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().status()).isEqualTo(Payment.Status.PENDING)
            );
        }

        @Test
        @DisplayName("비-PG 결제(CASH)에 verify를 요청하면 400 Bad Request를 반환한다.")
        void returnsBadRequest_whenPaymentIsOffline() {
            Order order = createOrder(americanoId, 1);
            PaymentCreateResponse cash = testRestTemplate.postForObject(ENDPOINT,
                    new PaymentCreateRequest(order.getId(), Payment.Method.CASH, null),
                    PaymentCreateResponse.class);

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(verifyEndpoint(cash.payment().id()), HttpMethod.POST,
                            HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("존재하지 않는 결제 ID면 404 Not Found를 반환한다.")
        void returnsNotFound_whenPaymentNotExists() {
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(verifyEndpoint(Long.MAX_VALUE), HttpMethod.POST,
                            HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("다른 매장 결제이면 404 Not Found를 반환한다.")
        void returnsNotFound_whenPaymentBelongsToOtherStore() {
            Payment payment = createOtherStorePgPayment();

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(verifyEndpoint(payment.getId()), HttpMethod.POST,
                            HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        private PaymentCreateResponse createPgPayment() {
            Order order = createOrder(americanoId, 1);
            return testRestTemplate.postForObject(ENDPOINT,
                    new PaymentCreateRequest(order.getId(), Payment.Method.EASY_PAY, Payment.Provider.KAKAO_PAY),
                    PaymentCreateResponse.class);
        }

        private String verifyEndpoint(Long paymentId) {
            return ENDPOINT + "/" + paymentId + "/verify";
        }
    }

    @Nested
    @DisplayName("결제 웹훅 호출 시, ")
    class Webhook {

        private static final String WEBHOOK_ENDPOINT = ENDPOINT + "/webhook/portone";

        @Test
        @DisplayName("유효한 서명이면 200 OK를 즉시 반환한다.")
        void returnsOk_whenSignatureValid() {
            PaymentCreateResponse created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-abc", null));
            String body = webhookJson("Transaction.Paid", created.pg().paymentId());

            ResponseEntity<Void> response = testRestTemplate.exchange(WEBHOOK_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(body, signedHeaders(body)), Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("pgPaymentId 기준 결제 상태가 PG 결과로 반영된다.")
        void reflectsGatewayResult() {
            PaymentCreateResponse created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-abc", null));
            String body = webhookJson("Transaction.Paid", created.pg().paymentId());

            testRestTemplate.exchange(WEBHOOK_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(body, signedHeaders(body)), Void.class);

            Payment persisted = paymentRepository.findById(created.payment().id()).orElseThrow();
            assertAll(
                    () -> assertThat(persisted.getStatus()).isEqualTo(Payment.Status.COMPLETED),
                    () -> assertThat(persisted.getPgTransactionKey()).isEqualTo("tx-abc")
            );
        }

        @Test
        @DisplayName("서명이 유효하면 존재하지 않는 paymentId라도 200 OK로 응답한다.")
        void returnsOk_whenPaymentNotFound() {
            String body = webhookJson("Transaction.Paid", "unknown-payment-id");

            ResponseEntity<Void> response = testRestTemplate.exchange(WEBHOOK_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(body, signedHeaders(body)), Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("URL 끝에 슬래시가 붙어도 200 OK로 응답한다. (PortOne이 trailing slash를 붙임)")
        void returnsOk_whenTrailingSlash() {
            PaymentCreateResponse created = createPgPayment();
            fakePaymentGateway.stub(created.pg().paymentId(),
                    new PaymentGatewayResult(PaymentGatewayResult.Status.PAID, AMERICANO_PRICE, "tx-abc", null));
            String body = webhookJson("Transaction.Paid", created.pg().paymentId());

            ResponseEntity<Void> response = testRestTemplate.exchange(WEBHOOK_ENDPOINT + "/", HttpMethod.POST,
                    new HttpEntity<>(body, signedHeaders(body)), Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("서명이 유효하지 않으면 401 Unauthorized를 반환한다.")
        void returns401_whenSignatureInvalid() {
            String body = webhookJson("Transaction.Paid", "any-payment-id");
            HttpHeaders headers = signedHeaders(body);
            headers.set("webhook-signature", "v1,aW52YWxpZHNpZ25hdHVyZQ==");

            ResponseEntity<Void> response = testRestTemplate.exchange(WEBHOOK_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("서명 헤더가 없으면 401 Unauthorized를 반환한다.")
        void returns401_whenHeadersMissing() {
            String body = webhookJson("Transaction.Paid", "any-payment-id");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Void> response = testRestTemplate.exchange(WEBHOOK_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        private PaymentCreateResponse createPgPayment() {
            Order order = createOrder(americanoId, 1);
            return testRestTemplate.postForObject(ENDPOINT,
                    new PaymentCreateRequest(order.getId(), Payment.Method.EASY_PAY, Payment.Provider.KAKAO_PAY),
                    PaymentCreateResponse.class);
        }

        private String webhookJson(String type, String pgPaymentId) {
            return "{\"type\":\"" + type + "\",\"data\":{\"paymentId\":\"" + pgPaymentId + "\"}}";
        }

        private HttpHeaders signedHeaders(String body) {
            String msgId = "msg_" + UUID.randomUUID().toString().replace("-", "");
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("webhook-id", msgId);
            headers.set("webhook-timestamp", timestamp);
            headers.set("webhook-signature", sign(msgId, timestamp, body));
            return headers;
        }

        private String sign(String msgId, String timestamp, String body) {
            try {
                String secret = portOneProperties.webhookSecret();
                byte[] key = Base64.getDecoder().decode(
                        secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret);
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key, "HmacSHA256"));
                byte[] signature = mac.doFinal((msgId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
                return "v1," + Base64.getEncoder().encodeToString(signature);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private Order createOrder(Long menuId, int quantity) {
        return orderFacade.createOrder(new OrderCreateCommand(
                storeId,
                List.of(new OrderItemLine(menuId, quantity))));
    }

    private Order createOtherStoreOrder() {
        Store otherStore = storeRepository.save(Store.create("2번 테스트 매장", "010-9999-9999"));
        Long otherMenuId = menuRepository.save(Menu.create(otherStore.getId(), "다른 매장 메뉴", 9_000, 1)).getId();
        return orderFacade.createOrder(new OrderCreateCommand(
                otherStore.getId(),
                List.of(new OrderItemLine(otherMenuId, 1))));
    }

    private Payment createOtherStorePgPayment() {
        Order order = createOtherStoreOrder();
        return paymentRepository.save(Payment.createPg(
                order.getId(),
                Payment.Provider.KAKAO_PAY,
                order.getTotalAmount(),
                "other-store-payment"));
    }
}
