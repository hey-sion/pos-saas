package com.sion.pos.support.portone;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.domain.payment.PaymentGatewayResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
}