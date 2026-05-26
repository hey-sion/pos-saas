package com.sion.pos.support.portone;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PortOneWebhookVerifierTest {

    // Standard Webhooks 공식 테스트 벡터 (https://github.com/standard-webhooks)
    private static final String SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
    private static final String MSG_ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
    private static final String TIMESTAMP = "1614265330";
    private static final String PAYLOAD = "{\"test\": 2432232314}";
    private static final String SIGNATURE = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=";

    // 벡터의 timestamp(2021) 를 허용 범위 안으로 두기 위해 시계를 고정
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.ofEpochSecond(1614265330L), ZoneOffset.UTC);

    private final PortOneWebhookVerifier verifier = new PortOneWebhookVerifier(SECRET, FIXED_CLOCK);

    @Nested
    @DisplayName("웹훅 서명 검증 시, ")
    class Verify {

        @Test
        @DisplayName("공식 테스트 벡터의 유효한 서명은 통과한다")
        void passesWithValidSignature() {
            assertThatCode(() -> verifier.verify(PAYLOAD, MSG_ID, SIGNATURE, TIMESTAMP))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("본문이 변조되면 검증에 실패한다")
        void failsWhenBodyTampered() {
            assertThatThrownBy(() -> verifier.verify(PAYLOAD + " ", MSG_ID, SIGNATURE, TIMESTAMP))
                    .isInstanceOf(WebhookVerificationException.class);
        }

        @Test
        @DisplayName("시크릿이 다르면 검증에 실패한다")
        void failsWithWrongSecret() {
            PortOneWebhookVerifier wrong =
                    new PortOneWebhookVerifier("whsec_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", FIXED_CLOCK);

            assertThatThrownBy(() -> wrong.verify(PAYLOAD, MSG_ID, SIGNATURE, TIMESTAMP))
                    .isInstanceOf(WebhookVerificationException.class);
        }

        @Test
        @DisplayName("timestamp가 허용 범위를 벗어나면 검증에 실패한다 (replay 방지)")
        void failsWhenTimestampOutOfTolerance() {
            PortOneWebhookVerifier nowVerifier = new PortOneWebhookVerifier(SECRET, Clock.systemUTC());

            assertThatThrownBy(() -> nowVerifier.verify(PAYLOAD, MSG_ID, SIGNATURE, TIMESTAMP))
                    .isInstanceOf(WebhookVerificationException.class);
        }

        @Test
        @DisplayName("공백으로 구분된 다중 서명 중 하나만 일치해도 통과한다 (키 로테이션)")
        void passesWhenOneOfMultipleSignaturesMatches() {
            String multi = "v1,aGVsbG8= " + SIGNATURE;

            assertThatCode(() -> verifier.verify(PAYLOAD, MSG_ID, multi, TIMESTAMP))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("필수 헤더가 null이면 검증에 실패한다")
        void failsWhenHeaderMissing() {
            assertThatThrownBy(() -> verifier.verify(PAYLOAD, null, SIGNATURE, TIMESTAMP))
                    .isInstanceOf(WebhookVerificationException.class);
        }

        @Test
        @DisplayName("서명 형식이 올바르지 않으면 검증에 실패한다")
        void failsWhenSignatureMalformed() {
            assertThatThrownBy(() -> verifier.verify(PAYLOAD, MSG_ID, "garbage-no-version", TIMESTAMP))
                    .isInstanceOf(WebhookVerificationException.class);
        }
    }
}