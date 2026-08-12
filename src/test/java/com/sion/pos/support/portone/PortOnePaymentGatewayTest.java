package com.sion.pos.support.portone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.domain.payment.PaymentGatewayResult;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class PortOnePaymentGatewayTest {

    @Nested
    @DisplayName("PortOne 결제 상태 매핑 시, ")
    class MapStatus {

        @Test
        @DisplayName("PAID는 PAID로 매핑된다.")
        void mapsPaid() {
            assertThat(PortOnePaymentGateway.mapStatus("PAID"))
                    .isEqualTo(PaymentGatewayResult.Status.PAID);
        }

        @ParameterizedTest
        @ValueSource(strings = {"FAILED", "CANCELLED", "PARTIAL_CANCELLED"})
        @DisplayName("실패/취소 계열은 모두 FAILED로 매핑된다.")
        void mapsFailureLikeToFailed(String raw) {
            assertThat(PortOnePaymentGateway.mapStatus(raw))
                    .isEqualTo(PaymentGatewayResult.Status.FAILED);
        }

        @ParameterizedTest
        @ValueSource(strings = {"READY", "PENDING", "VIRTUAL_ACCOUNT_ISSUED", "PAY_PENDING"})
        @DisplayName("진행 중/대기 계열은 모두 PENDING으로 매핑된다.")
        void mapsInProgressToPending(String raw) {
            assertThat(PortOnePaymentGateway.mapStatus(raw))
                    .isEqualTo(PaymentGatewayResult.Status.PENDING);
        }

        @Test
        @DisplayName("null이면 PENDING으로 매핑된다.")
        void mapsNullToPending() {
            assertThat(PortOnePaymentGateway.mapStatus(null))
                    .isEqualTo(PaymentGatewayResult.Status.PENDING);
        }

        @Test
        @DisplayName("알 수 없는 status는 PENDING으로 매핑된다.")
        void mapsUnknownToPending() {
            assertThat(PortOnePaymentGateway.mapStatus("FUTURE_NEW_STATUS"))
                    .isEqualTo(PaymentGatewayResult.Status.PENDING);
        }
    }

    @Nested
    @DisplayName("PortOne 결제 조회 시, ")
    class Lookup {

        private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);
        private static final Duration READ_TIMEOUT = Duration.ofMillis(200);
        private static final long SLOWER_THAN_READ_TIMEOUT_MILLIS = 1_500;

        private HttpServer stubServer;

        @AfterEach
        void stopStubServer() {
            stubServer.stop(0);
        }

        @Test
        @DisplayName("응답이 제때 오면 상태·금액·거래키를 매핑한다.")
        void mapsResponseWhenServerRespondsInTime() throws IOException {
            // Arrange
            startStubServer(0, "{\"status\":\"PAID\",\"amount\":{\"total\":12000},\"pgTxId\":\"tx-1\"}");
            PortOnePaymentGateway gateway = gatewayOfStubServer();

            // Act
            PaymentGatewayResult result = gateway.lookup("payment-1");

            // Assert
            assertThat(result.status()).isEqualTo(PaymentGatewayResult.Status.PAID);
            assertThat(result.amount()).isEqualTo(12_000);
            assertThat(result.transactionKey()).isEqualTo("tx-1");
        }

        @Test
        @DisplayName("읽기 타임아웃이 지나도 응답이 없으면 INTERNAL_ERROR 예외를 발생시킨다.")
        void throwsInternalErrorWhenReadTimeoutExceeded() throws IOException {
            // Arrange
            startStubServer(SLOWER_THAN_READ_TIMEOUT_MILLIS, "{\"status\":\"PAID\"}");
            PortOnePaymentGateway gateway = gatewayOfStubServer();

            // Act & Assert
            assertThatThrownBy(() -> gateway.lookup("payment-1"))
                    .isInstanceOfSatisfying(PosApplicationException.class,
                            e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR));
        }

        private void startStubServer(long delayMillis, String responseBody) throws IOException {
            stubServer = HttpServer.create(new InetSocketAddress(0), 0);
            stubServer.createContext("/payments", exchange -> {
                sleep(delayMillis);

                byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
                exchange.close();
            });
            stubServer.start();
        }

        private PortOnePaymentGateway gatewayOfStubServer() {
            PortOneProperties properties = new PortOneProperties(
                    "store-test",
                    "test-secret",
                    "http://localhost:" + stubServer.getAddress().getPort(),
                    "webhook-secret",
                    Map.of(),
                    new PortOneProperties.Timeout(CONNECT_TIMEOUT, READ_TIMEOUT));

            return new PortOnePaymentGateway(properties, RestClient.builder());
        }

        private void sleep(long millis) {
            if (millis <= 0) {
                return;
            }

            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}