package com.sion.pos.support.portone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.domain.payment.Payment;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.Duration;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PortOnePropertiesTest {

    private static final PortOneProperties.Timeout TIMEOUT =
            new PortOneProperties.Timeout(Duration.ofSeconds(2), Duration.ofSeconds(5));

    private static PortOneProperties propertiesWith(PortOneProperties.ChannelKey kakaoKey) {
        return new PortOneProperties(
                "store-test",
                "test-secret",
                "https://api.portone.io",
                "webhook-secret",
                Map.of(Payment.Provider.KAKAO_PAY.name(), kakaoKey),
                TIMEOUT);
    }

    @Nested
    @DisplayName("채널 키 조회 시, ")
    class ChannelKeyOf {

        @Test
        @DisplayName("테스트 모드면 테스트 채널 키를 반환한다")
        void returnsTestChannelKeyWhenNotLive() {
            PortOneProperties properties = propertiesWith(
                    new PortOneProperties.ChannelKey("channel-test", "channel-live"));

            String key = properties.channelKeyOf(Payment.Provider.KAKAO_PAY, false);

            assertThat(key).isEqualTo("channel-test");
        }

        @Test
        @DisplayName("실결제 모드면 실연동 채널 키를 반환한다")
        void returnsLiveChannelKeyWhenLive() {
            PortOneProperties properties = propertiesWith(
                    new PortOneProperties.ChannelKey("channel-test", "channel-live"));

            String key = properties.channelKeyOf(Payment.Provider.KAKAO_PAY, true);

            assertThat(key).isEqualTo("channel-live");
        }

        @Test
        @DisplayName("provider가 null이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsBadRequestWhenProviderNull() {
            PortOneProperties properties = propertiesWith(
                    new PortOneProperties.ChannelKey("channel-test", "channel-live"));

            expects(ErrorType.BAD_REQUEST, () -> properties.channelKeyOf(null, false));
        }

        @Test
        @DisplayName("요청한 모드의 채널 키가 비어 있으면 INTERNAL_ERROR 예외를 발생시킨다")
        void throwsInternalErrorWhenKeyBlank() {
            PortOneProperties properties = propertiesWith(
                    new PortOneProperties.ChannelKey("channel-test", ""));

            expects(ErrorType.INTERNAL_ERROR, () -> properties.channelKeyOf(Payment.Provider.KAKAO_PAY, true));
        }

        @Test
        @DisplayName("provider의 채널 설정이 아예 없으면 INTERNAL_ERROR 예외를 발생시킨다")
        void throwsInternalErrorWhenProviderNotConfigured() {
            PortOneProperties properties = new PortOneProperties(
                    "store-test", "test-secret", "https://api.portone.io", "webhook-secret", Map.of(), TIMEOUT);

            expects(ErrorType.INTERNAL_ERROR, () -> properties.channelKeyOf(Payment.Provider.KAKAO_PAY, false));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}